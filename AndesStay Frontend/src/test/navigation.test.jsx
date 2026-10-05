import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Navbar from "../components/Navbar";
import Login from "../pages/Login";

const session = vi.hoisted(() => ({
    account: { name: "Camila Torres", idTokenClaims: { roles: ["Admin"] } },
    authenticated: false,
    instance: { loginRedirect: vi.fn(), logoutRedirect: vi.fn() },
}));
vi.mock("@azure/msal-react", () => ({
    useMsal: () => ({ instance: session.instance, accounts: [session.account] }),
    useIsAuthenticated: () => session.authenticated,
}));
beforeEach(() => {
    session.account = { name: "Camila Torres", idTokenClaims: { roles: ["Admin"] } };
    session.authenticated = false;
    vi.clearAllMocks();
});
const show = (component, route = "/dashboard") => render(<MemoryRouter initialEntries={[route]}>{component}</MemoryRouter>);

describe("Navegación adaptable y acceso", () => {
    it("conserva las opciones del huésped y marca la página actual", () => {
        session.account.idTokenClaims.roles = ["Huesped"];
        show(<Navbar />, "/catalog");
        const nav = within(screen.getByRole("navigation", { name: "Navegación principal" }));
        expect(nav.getByRole("link", { name: "Catálogo" }).getAttribute("aria-current")).toBe("page");
        expect(nav.getByRole("link", { name: "Reservas" })).toBeTruthy();
        expect(nav.queryByRole("link", { name: "Reportes" })).toBeNull();
        expect(nav.queryByRole("link", { name: "Auditoría" })).toBeNull();
    });
    it("abre el menú, conserva el foco y restaura el desplazamiento al cerrar con Escape", async () => {
        document.body.style.overflow = "auto";
        show(<Navbar />);
        const button = screen.getByRole("button", { name: "Abrir menú" });
        fireEvent.click(button);
        expect(button.getAttribute("aria-expanded")).toBe("true");
        expect(document.body.style.overflow).toBe("hidden");
        const firstLink = screen.getByRole("link", { name: "AndesStay, inicio" });
        await waitFor(() => expect(document.activeElement).toBe(firstLink));
        screen.getByRole("button", { name: "Cerrar sesión" }).focus();
        fireEvent.keyDown(document, { key: "Tab" });
        expect(document.activeElement).toBe(firstLink);
        fireEvent.keyDown(document, { key: "Escape" });
        expect(button.getAttribute("aria-expanded")).toBe("false");
        expect(document.body.style.overflow).toBe("auto");
        expect(document.activeElement).toBe(button);
        document.body.style.overflow = "";
    });
    it("cierra el menú al seleccionar una pantalla", () => {
        show(<Navbar />);
        fireEvent.click(screen.getByRole("button", { name: "Abrir menú" }));
        fireEvent.click(screen.getByRole("link", { name: "Reservas", exact: true }));
        expect(screen.getByRole("button", { name: "Abrir menú" }).getAttribute("aria-expanded")).toBe("false");
    });
    it("permite reintentar cuando Microsoft no puede iniciar sesión", async () => {
        session.instance.loginRedirect.mockRejectedValue(new Error("Error simulado"));
        show(<Login />, "/login");
        fireEvent.click(screen.getByRole("button", { name: "Iniciar sesión con mi correo" }));
        expect((await screen.findByRole("alert")).textContent).toContain("Vuelve a intentarlo");
        expect(screen.getByRole("button", { name: "Iniciar sesión con mi correo" }).disabled).toBe(false);
        expect(session.instance.loginRedirect).toHaveBeenCalledTimes(1);
    });
    it("evita solicitudes de acceso repetidas mientras está conectando", () => {
        session.instance.loginRedirect.mockImplementation(() => new Promise(() => {}));
        show(<Login />, "/login");
        fireEvent.click(screen.getByRole("button", { name: "Iniciar sesión con mi correo" }));
        const button = screen.getByRole("button", { name: "Conectando con Microsoft…" });
        expect(button.disabled).toBe(true);
        fireEvent.click(button);
        expect(session.instance.loginRedirect).toHaveBeenCalledTimes(1);
    });
});
