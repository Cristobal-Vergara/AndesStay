import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ReservationNotifications from "../components/ReservationNotifications";

const session = vi.hoisted(() => ({
    account: { username: "ana@example.test", idTokenClaims: { roles: ["Huesped"] } },
    instance: { acquireTokenSilent: vi.fn() },
}));
vi.mock("@azure/msal-react", () => ({
    useMsal: () => ({ instance: session.instance, accounts: [session.account] }),
}));
const notice = { id: 1, reservationId: 7, unitId: 2, status: "CONFIRMADA", title: "Tu reserva #7 confirmada", occurredAt: "2026-10-05T14:00:00Z" };
const page = (content, number = 0, totalPages = 1) => ({ content, page: number, size: 5, totalPages, totalElements: totalPages === 2 ? 6 : content.length });
let calls;
let responder;
beforeEach(() => {
    calls = [];
    session.instance.acquireTokenSilent.mockResolvedValue({ accessToken: "test-token" });
    responder = url => new Response(JSON.stringify(page([notice], Number(url.searchParams.get("page")), 2)), { status: 200 });
    vi.stubGlobal("fetch", vi.fn(async (path, options) => {
        const url = new URL(path, "http://localhost");
        calls.push({ url, options });
        return responder(url);
    }));
});
afterEach(() => vi.unstubAllGlobals());

it("muestra un aviso de huésped y pagina usando el token sin enviar destinatarios", async () => {
    render(<ReservationNotifications />);
    await screen.findByText("Tu reserva #7 confirmada");
    expect(screen.getByRole("heading", { name: "Mis avisos" })).toBeTruthy();
    expect(screen.getByText("Reserva #7 · Unidad 2")).toBeTruthy();
    expect(document.querySelector("time").dateTime).toBe(notice.occurredAt);
    fireEvent.click(screen.getByRole("button", { name: "Siguiente" }));
    await waitFor(() => expect(calls.at(-1).url.searchParams.get("page")).toBe("1"));
    expect(calls.at(-1).url.searchParams.get("size")).toBe("5");
    expect([...calls.at(-1).url.searchParams.keys()]).toEqual(["page", "size"]);
    expect(calls.at(-1).options.headers.Authorization).toBe("Bearer test-token");
});

it("actualizar trae el nuevo aviso después de una confirmación asíncrona", async () => {
    responder = () => new Response(JSON.stringify(page([])), { status: 200 });
    render(<ReservationNotifications staff />);
    await screen.findByText("Los próximos movimientos de reservas aparecerán aquí.");
    responder = () => new Response(JSON.stringify(page([{ ...notice, title: "Reserva #7 confirmada" }])), { status: 200 });
    fireEvent.click(screen.getByRole("button", { name: "Actualizar avisos" }));
    await screen.findByText("Reserva #7 confirmada");
    expect(screen.getByRole("heading", { name: "Actividad de reservas" })).toBeTruthy();
    expect(calls).toHaveLength(2);
});

it("un fallo de entrega no aparece como una lista vacía y permite reintentar la consulta", async () => {
    responder = () => new Response('{"detail":"Servicio temporalmente no disponible"}', { status: 503 });
    render(<ReservationNotifications />);
    expect((await screen.findByRole("alert")).textContent).toBe("El servicio no está disponible. Vuelve a intentarlo.");
    expect(screen.queryByText("Todo al día")).toBeNull();
    responder = () => new Response(JSON.stringify(page([notice])), { status: 200 });
    fireEvent.click(screen.getByRole("button", { name: "Actualizar avisos" }));
    await screen.findByText("Tu reserva #7 confirmada");
});
