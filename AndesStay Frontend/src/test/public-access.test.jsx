import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AppRoutes } from "../App";
import AccountProvider from "../components/AccountProvider";

const session = vi.hoisted(() => ({
    authenticated: true,
    account: { name: "Ana Torres", username: "verified@example.test", idTokenClaims: { roles: [] } },
    instance: { acquireTokenSilent: vi.fn(), logoutRedirect: vi.fn(), loginRedirect: vi.fn() },
}));
vi.mock("@azure/msal-react", () => ({
    useMsal: () => ({ instance: session.instance, accounts: session.authenticated ? [session.account] : [] }),
    useIsAuthenticated: () => session.authenticated,
}));
let registered, granted, unavailable, calls;
beforeEach(() => {
    vi.clearAllMocks();
    session.authenticated = true;
    session.account = { name: "Ana Torres", username: "verified@example.test", idTokenClaims: { roles: [] } };
    session.instance.acquireTokenSilent.mockResolvedValue({ accessToken: "verified-token" });
    registered = false; granted = []; unavailable = false; calls = [];
    vi.stubGlobal("fetch", vi.fn(async (path, options) => {
        const url = new URL(path, "http://localhost"); calls.push({ url, options });
        if (url.pathname.endsWith("/account/profile") || url.pathname.endsWith("/account/register")) {
            if (unavailable) return new Response('{"detail":"internal"}', { status: 503 });
            if (options.method === "POST") { registered = true; granted = ["Huesped"]; }
            return new Response(JSON.stringify({ registered, roles: granted, displayName: "Ana Torres", email: "verified@example.test" }),
                { status: options.method === "POST" ? 201 : 200 });
        }
        return new Response(JSON.stringify({ content: [], page: 0, size: 20, totalPages: 0, totalElements: 0 }), { status: 200 });
    }));
});
afterEach(() => vi.unstubAllGlobals());
const show = (route = "/dashboard") => render(<AccountProvider><MemoryRouter initialEntries={[route]}><AppRoutes /></MemoryRouter></AccountProvider>);

describe("Acceso público con permisos confirmados por el backend", () => {
    it("la página pública se muestra sin consultar perfiles ni iniciar sesión", async () => {
        session.authenticated = false;
        show("/register");
        await screen.findByRole("button", { name: "Registrarme con mi correo" });
        expect(calls).toHaveLength(0);
    });
    it("una cuenta nueva completa el registro y entra como huésped sin roles en el ID token", async () => {
        show();
        await screen.findByLabelText("Nombre completo");
        expect(calls.some(call => call.url.pathname.endsWith("/reservations"))).toBe(false);
        fireEvent.click(screen.getByRole("button", { name: "Completar registro" }));
        await screen.findByText(/Tu acceso como huésped está listo/);
        fireEvent.click(screen.getByRole("link", { name: "Ir a mi espacio" }));
        await screen.findByText("Mis últimas reservas");
        const nav = within(screen.getByRole("navigation", { name: "Navegación principal" }));
        expect(nav.getByRole("link", { name: "Reservas" })).toBeTruthy();
        expect(nav.getByRole("link", { name: "Catálogo" })).toBeTruthy();
        expect(nav.queryByRole("link", { name: "Reportes" })).toBeNull();
        expect(nav.queryByRole("link", { name: "Auditoría" })).toBeNull();
        expect(within(screen.getByRole("link", { name: "Mi perfil en AndesStay" })).getByText("Huésped")).toBeTruthy();
        expect(session.account.idTokenClaims.roles).toEqual([]);
        expect(calls.filter(call => call.options.method === "POST")).toHaveLength(1);
    });
    it("un perfil guardado como huésped puede abrir reservas directamente", async () => {
        registered = true; granted = ["Huesped"];
        show("/reservations");
        await screen.findByRole("navigation", { name: "Navegación principal" });
        await waitFor(() => expect(calls.some(call => call.url.pathname.endsWith("/reservations"))).toBe(true));
        expect(within(screen.getByRole("link", { name: "Mi perfil en AndesStay" })).getByText("Huésped")).toBeTruthy();
    });
    it("un rol Admin del ID token no abre reportes si el servidor devuelve solo Huesped", async () => {
        session.account.idTokenClaims.roles = ["Admin"];
        registered = true; granted = ["Huesped"];
        show("/reports");
        await screen.findByText("Mis últimas reservas");
        expect(calls.some(call => call.url.pathname.endsWith("/reports"))).toBe(false);
        expect(screen.queryByRole("link", { name: "Reportes" })).toBeNull();
    });
    it("un error al comprobar la cuenta bloquea las páginas privadas y permite reintentar", async () => {
        unavailable = true;
        show("/catalog");
        await screen.findByRole("alert");
        expect(calls.some(call => call.url.pathname.endsWith("/catalog"))).toBe(false);
        unavailable = false;
        fireEvent.click(screen.getByRole("button", { name: "Volver a intentar" }));
        await screen.findByLabelText("Nombre completo");
        expect(calls.some(call => call.url.pathname.endsWith("/catalog"))).toBe(false);
    });
});
