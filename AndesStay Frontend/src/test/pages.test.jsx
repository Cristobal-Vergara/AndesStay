import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Audit from "../pages/Audit";
import Catalog from "../pages/Catalog";
import Reservations from "../pages/Reservations";

const session = vi.hoisted(() => ({
    account: { username: "usuario@example.test", idTokenClaims: { roles: ["Admin"] } },
    instance: { acquireTokenSilent: vi.fn(), logoutRedirect: vi.fn() },
}));
vi.mock("@azure/msal-react", () => ({
    useMsal: () => ({ instance: session.instance, accounts: [session.account] }),
}));
const page = (content, number = 0, totalPages = 1, totalElements = content.length) => ({ content, page: number, size: 20, totalPages, totalElements });
const unit = { id: 1, name: "Cabaña de prueba", type: "CABANA", capacity: 4, price: 80000, available: true, description: "Prueba" };
const reservation = { id: 7, guestName: "Ana", unitId: 1, checkIn: "2030-01-10", checkOut: "2030-01-12", status: "CREADA", guestAssigned: false };
const event = { id: 2, action: "RESERVA_CREADA", reservationId: 7, user: "recepcion@example.test", description: "Reserva registrada", eventDate: "2026-10-03T10:00:00" };
let calls;
let responder;
beforeEach(() => {
    calls = [];
    session.account.idTokenClaims.roles = ["Admin"];
    session.instance.acquireTokenSilent.mockResolvedValue({ accessToken: "test-token" });
    responder = (url, options) => {
        if (options.method !== "GET") return new Response('{"id":1}', { status: 200 });
        if (url.pathname.endsWith("/audit")) return new Response(JSON.stringify(page([event])), { status: 200 });
        if (url.pathname.endsWith("/catalog")) return new Response(JSON.stringify(page([unit])), { status: 200 });
        if (url.pathname.endsWith("/account")) return new Response(JSON.stringify({ objectId: "11111111-1111-1111-1111-111111111111" }), { status: 200 });
        return new Response(JSON.stringify(page([reservation])), { status: 200 });
    };
    vi.stubGlobal("fetch", vi.fn(async (path, options) => {
        const url = new URL(path, "http://localhost");
        calls.push({ url, options });
        return responder(url, options);
    }));
});
afterEach(() => vi.unstubAllGlobals());
const show = component => render(<MemoryRouter>{component}</MemoryRouter>);

describe("auditoría", () => {
    it("filtra y después limpia sin reenviar los valores anteriores", async () => {
        show(<Audit />);
        await screen.findByText("Reserva registrada");
        fireEvent.change(screen.getByLabelText("Usuario"), { target: { value: "ana+ventas@example.test" } });
        fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2026-10-01T10:00" } });
        fireEvent.click(screen.getByRole("button", { name: "Filtrar" }));
        await waitFor(() => expect(calls.at(-1).url.searchParams.get("user")).toBe("ana+ventas@example.test"));
        await waitFor(() => expect(screen.getByRole("button", { name: "Limpiar filtros" }).disabled).toBe(false));
        fireEvent.click(screen.getByRole("button", { name: "Limpiar filtros" }));
        await waitFor(() => expect(calls.at(-1).url.searchParams.has("user")).toBe(false));
        expect(calls.at(-1).url.searchParams.has("inicio")).toBe(false);
        expect(screen.getByLabelText("Usuario").value).toBe("");
    });
    it("solicita la siguiente página conservando el filtro aplicado", async () => {
        responder = (url) => new Response(JSON.stringify(page([event], Number(url.searchParams.get("page")), 2, 21)), { status: 200 });
        show(<Audit />);
        await screen.findByText("Reserva registrada");
        fireEvent.change(screen.getByLabelText("Tipo de evento"), { target: { value: "RESERVA_CREADA" } });
        fireEvent.click(screen.getByRole("button", { name: "Filtrar" }));
        await waitFor(() => expect(calls.at(-1).url.searchParams.get("action")).toBe("RESERVA_CREADA"));
        await waitFor(() => expect(screen.getByRole("button", { name: "Siguiente" }).disabled).toBe(false));
        fireEvent.click(screen.getByRole("button", { name: "Siguiente" }));
        await waitFor(() => expect(calls.at(-1).url.searchParams.get("page")).toBe("1"));
        expect(calls.at(-1).url.searchParams.get("action")).toBe("RESERVA_CREADA");
    });
});

describe("catálogo", () => {
    it("permite al huésped consultar y oculta las operaciones de gestión", async () => {
        session.account.idTokenClaims.roles = ["Huesped"];
        show(<Catalog />);
        await screen.findByText("Cabaña de prueba");
        expect(screen.queryByRole("button", { name: "Crear unidad" })).toBe(null);
        expect(screen.queryByRole("button", { name: "Eliminar" })).toBe(null);
        expect(calls.every(call => call.options.method === "GET")).toBe(true);
    });
    it("edita la tarifa sin perder la disponibilidad ni la capacidad", async () => {
        show(<Catalog />);
        await screen.findByText("Cabaña de prueba");
        fireEvent.click(screen.getByRole("button", { name: "Editar" }));
        fireEvent.change(screen.getByLabelText("Tarifa por noche"), { target: { value: "90000.50" } });
        fireEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));
        await waitFor(() => expect(calls.some(call => call.options.method === "PUT")).toBe(true));
        const update = calls.find(call => call.options.method === "PUT");
        expect(update.url.pathname).toBe("/api/v1/bff/catalog/1");
        expect(JSON.parse(update.options.body)).toMatchObject({ price: "90000.50", capacity: 4, available: true });
    });
    it("muestra la explicación del backend cuando la unidad tiene reservas", async () => {
        vi.spyOn(window, "confirm").mockReturnValue(true);
        responder = (_url, options) => options.method === "DELETE"
            ? new Response('{"detail":"La unidad tiene reservas asociadas"}', { status: 409 })
            : new Response(JSON.stringify(page([unit])), { status: 200 });
        show(<Catalog />);
        await screen.findByText("Cabaña de prueba");
        fireEvent.click(screen.getByRole("button", { name: "Eliminar" }));
        expect((await screen.findByRole("alert")).textContent).toBe("La unidad tiene reservas asociadas");
        vi.restoreAllMocks();
    });
});

describe("reservas", () => {
    it("muestra al huésped su identificador y oculta edición y cambios de estado", async () => {
        session.account.idTokenClaims.roles = ["Huesped"];
        show(<Reservations />);
        await screen.findByText("11111111-1111-1111-1111-111111111111");
        expect(screen.queryByRole("button", { name: "Editar" })).toBe(null);
        expect(screen.queryByLabelText("Cambiar estado de reserva 7")).toBe(null);
    });
    it("solo ofrece las transiciones válidas y envía la nueva confirmación", async () => {
        show(<Reservations />);
        const select = await screen.findByLabelText("Cambiar estado de reserva 7");
        const values = within(select).getAllByRole("option").map(option => option.value);
        expect(values).toEqual(["", "CONFIRMADA", "CANCELADA"]);
        fireEvent.change(select, { target: { value: "CONFIRMADA" } });
        await waitFor(() => expect(calls.some(call => call.url.pathname.endsWith("/status"))).toBe(true));
        expect(JSON.parse(calls.find(call => call.url.pathname.endsWith("/status")).options.body)).toEqual({ status: "CONFIRMADA" });
    });
    it("asigna explícitamente el identificador introducido por recepción", async () => {
        show(<Reservations />);
        await screen.findByText("Ana");
        fireEvent.click(screen.getByRole("button", { name: "Asignar huésped" }));
        const id = "33333333-3333-3333-3333-333333333333";
        fireEvent.change(screen.getByLabelText("Identificador de cuenta del huésped"), { target: { value: id } });
        fireEvent.click(screen.getByRole("button", { name: "Asignar cuenta" }));
        await waitFor(() => expect(calls.some(call => call.url.pathname.endsWith("/owner"))).toBe(true));
        expect(JSON.parse(calls.find(call => call.url.pathname.endsWith("/owner")).options.body)).toEqual({ objectId: id });
    });
});
