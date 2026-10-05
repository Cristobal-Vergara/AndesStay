import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import MessagingHealth from "../components/MessagingHealth";

const session = vi.hoisted(() => ({
    account: { username: "ana@example.test", idTokenClaims: { roles: ["Admin"] } },
    instance: { acquireTokenSilent: vi.fn() },
}));
vi.mock("@azure/msal-react", () => ({
    useMsal: () => ({ instance: session.instance, accounts: [session.account] }),
}));
const healthy = { available: true, pending: 2, quarantined: 0, receptionConsumers: 1, guestConsumers: 1, demoEnabled: true, demoQuarantined: 0 };
let state;
beforeEach(() => {
    state = { ...healthy };
    session.instance.acquireTokenSilent.mockResolvedValue({ accessToken: "test-token" });
    vi.stubGlobal("fetch", vi.fn(async (path, options) => new Response(JSON.stringify(options.method === "POST" ? { eventId: "demo" } : state), { status: options.method === "POST" ? 202 : 200 })));
});
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("muestra conteos reales y refresca automáticamente con el JWT", async () => {
    const interval = vi.spyOn(globalThis, "setInterval");
    render(<MessagingHealth />);
    await screen.findByText("Entrega activa");
    expect(interval.mock.calls[0][1]).toBe(10000);
    state = { ...healthy, quarantined: 3 };
    await act(async () => { await interval.mock.calls[0][0](); });
    expect(screen.getByText("Hay avisos por revisar")).toBeTruthy();
    expect(screen.getByRole("alert").textContent).toContain("7 días");
    expect(fetch.mock.calls.at(-1)[1].headers.Authorization).toBe("Bearer test-token");
});
it("una desconexión no se presenta como cero avisos fallidos", async () => {
    state = { available: false, pending: null, quarantined: null };
    render(<MessagingHealth />);
    await screen.findByText("Estado sin verificar");
    expect(screen.queryByText("En cuarentena")).toBeNull();
    state = healthy;
    fireEvent.click(screen.getByRole("button", { name: "Actualizar estado" }));
    await screen.findByText("Entrega activa");
});
it("un consumidor detenido genera una alerta", async () => {
    state = { ...healthy, guestConsumers: 0 };
    render(<MessagingHealth />);
    await screen.findByText("Entrega en pausa");
    expect(screen.getByRole("alert").textContent).toContain("consumidores");
});
it("la prueba de administrador publica sin datos de reservas y muestra su cuarentena separada", async () => {
    render(<MessagingHealth canDemo />);
    await screen.findByText("Entrega activa");
    fireEvent.click(screen.getByRole("button", { name: "Probar alerta" }));
    await waitFor(() => expect(fetch.mock.calls.some(([,options]) => options.method === "POST")).toBe(true));
    const publish = fetch.mock.calls.find(([,options]) => options.method === "POST");
    expect(publish[0]).toBe("/api/bff/messaging/demo");
    expect(publish[1].body).toBeUndefined();
    await screen.findByText(/Prueba enviada/);
    state = { ...healthy, demoQuarantined: 1 };
    fireEvent.click(screen.getByRole("button", { name: "Actualizar estado" }));
    await screen.findByText(/Prueba local: 1 mensaje/);
    expect(screen.getByText("Entrega activa")).toBeTruthy();
});
it("recepción no puede iniciar la prueba y la demo deshabilitada se oculta", async () => {
    state = { ...healthy, demoEnabled: false };
    render(<MessagingHealth />);
    await screen.findByText("Entrega activa");
    expect(screen.queryByRole("button", { name: "Probar alerta" })).toBeNull();
    expect(screen.queryByText("Prueba local")).toBeNull();
});
it("al desmontar se detiene el sondeo", async () => {
    const view = render(<MessagingHealth />);
    await screen.findByText("Entrega activa");
    vi.useFakeTimers(); const before = fetch.mock.calls.length;
    view.unmount(); await vi.advanceTimersByTimeAsync(20000);
    expect(fetch.mock.calls).toHaveLength(before);
});
