import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import AccountProvider from "../components/AccountProvider";
import { AppRoutes } from "../App";

const session = vi.hoisted(() => ({
    account: { name: "Cuenta de prueba", idTokenClaims: { roles: ["Admin"] } },
    instance: { acquireTokenSilent: vi.fn(), loginRedirect: vi.fn(), logoutRedirect: vi.fn() },
}));
vi.mock("@azure/msal-react", () => ({
    useIsAuthenticated: () => true,
    useMsal: () => ({ instance: session.instance, accounts: [session.account] }),
}));
const routes = [
    { path: "/reservations", api: "/api/v1/bff/reservations", label: "Reservas", roles: ["Admin", "Recepcionista", "Huesped"] },
    { path: "/catalog", api: "/api/v1/bff/catalog", label: "Catálogo", roles: ["Admin", "Recepcionista", "Huesped"] },
    { path: "/reports", api: "/api/v1/bff/reports", label: "Reportes", roles: ["Admin"] },
    { path: "/audit", api: "/api/v1/bff/audit", label: "Auditoría", roles: ["Admin", "Auditor"] },
];
let role, calls;
beforeEach(() => {
    vi.clearAllMocks();
    calls = [];
    session.instance.acquireTokenSilent.mockResolvedValue({ accessToken: "test-access-token" });
    vi.stubGlobal("fetch", vi.fn(async (path, options) => {
        const url = new URL(path, "http://localhost");
        calls.push({ path: url.pathname, options });
        let data = { content: [], page: 0, size: 20, totalPages: 0, totalElements: 0 };
        if (url.pathname.endsWith("/account/profile")) data = { registered: role === "Huesped", roles: [role], displayName: "Cuenta de prueba" };
        if (url.pathname.endsWith("/reports/operations")) data = { fecha: "2026-10-13", timeZone: "America/Santiago", llegadas: [], salidas: [] };
        if (url.pathname.endsWith("/messaging/listeners")) data = [];
        if (url.pathname.endsWith("/messaging/status")) data = { connected: true, ready: 0, unacknowledged: 0, quarantined: 0, demoEnabled: false };
        return new Response(JSON.stringify(data), { status: 200 });
    }));
});
afterEach(() => vi.unstubAllGlobals());

describe("Rutas por permisos efectivos de la API", () => {
    const cases = ["Admin", "Recepcionista", "Huesped", "Auditor"].flatMap(accountRole => routes.map(route => ({
        role: accountRole, ...route, allowed: route.roles.includes(accountRole),
    })));
    it.each(cases)("$role en $path: acceso=$allowed", async ({ role: accountRole, path, api, label, allowed }) => {
        role = accountRole;
        render(<AccountProvider><MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter></AccountProvider>);
        const navigation = within(await screen.findByRole("navigation", { name: "Navegación principal" }));
        for (const route of routes) {
            const link = navigation.queryByRole("link", { name: route.label });
            expect(!!link).toBe(route.roles.includes(role));
        }
        if (allowed) {
            expect(navigation.getByRole("link", { name: label }).getAttribute("aria-current")).toBe("page");
            await waitFor(() => expect(calls.some(call => call.path === api)).toBe(true));
            expect(calls.find(call => call.path === api).options.headers.Authorization).toBe("Bearer test-access-token");
        } else {
            expect(navigation.getByRole("link", { name: "Inicio" }).getAttribute("aria-current")).toBe("page");
            expect(calls.some(call => call.path === api)).toBe(false);
        }
    });
});
