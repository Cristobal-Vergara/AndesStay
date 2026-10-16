import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import ListenerControls from "../components/ListenerControls";

const session = vi.hoisted(() => ({
    account: { username: "ana@example.test", idTokenClaims: { roles: ["Admin"] } },
    instance: { acquireTokenSilent: vi.fn() },
}));
vi.mock("@azure/msal-react", () => ({ useMsal: () => ({ instance: session.instance, accounts: [session.account] }) }));
const reception = { id: "receptionNotifications", label: "Avisos de recepción", queues: ["reception"], state: "RUNNING" };
const guest = { id: "guestNotifications", label: "Avisos de huéspedes", queues: ["guest"], state: "RUNNING" };
const json = (value, status = 200) => new Response(JSON.stringify(value), { status });

beforeEach(() => {
    session.instance.acquireTokenSilent.mockResolvedValue({ accessToken: "test-token" });
    vi.stubGlobal("fetch", vi.fn(async () => json([reception, guest])));
});
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("consulta con JWT y permite a recepción ver el estado sin botones de control", async () => {
    render(<ListenerControls />);
    await screen.findByText("Avisos de recepción");
    expect(screen.getAllByText("Iniciado")).toHaveLength(2);
    expect(screen.queryByRole("button", { name: /Pausar avisos/ })).toBeNull();
    expect(fetch.mock.calls[0][0]).toBe("/api/v1/bff/messaging/listeners");
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe("Bearer test-token");
});

it("pausa y reanuda un consumidor con la respuesta del servidor", async () => {
    fetch.mockImplementation(async (path, options) => options.method === "POST"
        ? json({ ...reception, state: path.endsWith("/pause") ? "STOPPED" : "RUNNING" })
        : json([reception, guest]));
    render(<ListenerControls canManage />);
    fireEvent.click(await screen.findByRole("button", { name: "Pausar avisos de recepción" }));
    fireEvent.click(await screen.findByRole("button", { name: "Reanudar avisos de recepción" }));
    await screen.findByRole("button", { name: "Pausar avisos de recepción" });
    const mutations = fetch.mock.calls.filter(([, options]) => options.method === "POST");
    expect(mutations.map(([path]) => path)).toEqual([
        "/api/v1/bff/messaging/listeners/receptionNotifications/pause",
        "/api/v1/bff/messaging/listeners/receptionNotifications/resume",
    ]);
    expect(mutations.every(([, options]) => options.body === undefined && options.headers.Authorization === "Bearer test-token")).toBe(true);
});

it("una pausa aceptada continúa pendiente y se completa al consultar", async () => {
    const interval = vi.spyOn(globalThis, "setInterval");
    let state = "RUNNING";
    fetch.mockImplementation(async (_path, options) => {
        if (options.method === "POST") { state = "STOPPING"; return json({ ...reception, state }, 202); }
        return json([{ ...reception, state }]);
    });
    render(<ListenerControls canManage />);
    fireEvent.click(await screen.findByRole("button", { name: "Pausar avisos de recepción" }));
    await screen.findByText("Pausando…");
    expect(screen.getByRole("button", { name: "Pausar avisos de recepción" }).disabled).toBe(true);
    expect(screen.queryByRole("button", { name: "Reanudar avisos de recepción" })).toBeNull();
    state = "STOPPED";
    await act(async () => { await interval.mock.calls[0][0](); });
    expect(screen.getByRole("button", { name: "Reanudar avisos de recepción" }).disabled).toBe(false);
});

it("una lectura anterior a la pausa no reemplaza el nuevo estado", async () => {
    let resolveRead;
    let reads = 0;
    fetch.mockImplementation(async (_path, options) => {
        if (options.method === "POST") return json({ ...reception, state: "STOPPED" });
        if (++reads === 1) return json([reception]);
        return new Promise(resolve => { resolveRead = resolve; });
    });
    render(<ListenerControls canManage />);
    await screen.findByRole("button", { name: "Pausar avisos de recepción" });
    fireEvent.click(screen.getByRole("button", { name: "Actualizar consumidores" }));
    await waitFor(() => expect(resolveRead).toBeTypeOf("function"));
    fireEvent.click(screen.getByRole("button", { name: "Pausar avisos de recepción" }));
    await screen.findByRole("button", { name: "Reanudar avisos de recepción" });
    await act(async () => { resolveRead(json([reception])); });
    expect(screen.getByRole("button", { name: "Reanudar avisos de recepción" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Pausar avisos de recepción" })).toBeNull();
});

it("un error de permisos no se presenta como una pausa exitosa", async () => {
    fetch.mockImplementation(async (_path, options) => options.method === "POST"
        ? json({ detail: "No tienes permiso para realizar esta operación." }, 403) : json([reception]));
    render(<ListenerControls canManage />);
    fireEvent.click(await screen.findByRole("button", { name: "Pausar avisos de recepción" }));
    expect((await screen.findByRole("alert")).textContent).toContain("No tienes permiso");
    expect(screen.queryByRole("button", { name: "Reanudar avisos de recepción" })).toBeNull();
});

it("una respuesta inválida no muestra controles ni un estado saludable", async () => {
    fetch.mockResolvedValue(json([{ ...reception, state: "UNKNOWN" }]));
    render(<ListenerControls canManage />);
    await screen.findByRole("alert");
    expect(screen.queryByText("Iniciado")).toBeNull();
    expect(screen.queryByRole("button", { name: "Pausar avisos de recepción" })).toBeNull();
});

it("evita consultas simultáneas y cancela la lectura al salir de la página", async () => {
    const interval = vi.spyOn(globalThis, "setInterval");
    fetch.mockImplementation(() => new Promise(() => {}));
    const view = render(<ListenerControls />);
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    await act(async () => { await interval.mock.calls[0][0](); });
    expect(fetch).toHaveBeenCalledTimes(1);
    const signal = fetch.mock.calls[0][1].signal;
    view.unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => { await interval.mock.calls[0][0](); });
    expect(fetch).toHaveBeenCalledTimes(1);
});
