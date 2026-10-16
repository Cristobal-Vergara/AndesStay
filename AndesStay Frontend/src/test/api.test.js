import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchJson, queryPath } from "../api";

afterEach(() => vi.unstubAllGlobals());
describe("cliente HTTP", () => {
    it("preserva signos en filtros y no envía campos vacíos", () => {
        const path = queryPath("/api/v1/bff/audit", { user: "ana+ventas & equipo", inicio: "", page: 0, size: 20 });
        const url = new URL(path, "http://localhost");
        expect(url.searchParams.get("user")).toBe("ana+ventas & equipo");
        expect(url.searchParams.get("inicio")).toBe(null);
        expect(url.searchParams.get("page")).toBe("0");
    });
    it("envía el token y el cuerpo de una edición", async () => {
        const fetch = vi.fn().mockResolvedValue(new Response('{"id":1}', { status: 200 }));
        vi.stubGlobal("fetch", fetch);
        expect(await fetchJson("/api/v1/bff/catalog/1", "token", { method: "PUT", body: { price: "50000.50" } })).toEqual({ id: 1 });
        const request = fetch.mock.calls[0][1];
        expect(request.headers.Authorization).toBe("Bearer token");
        expect(JSON.parse(request.body).price).toBe("50000.50");
    });
    it("muestra el detail de un conflicto y conserva el código", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response('{"detail":"La unidad ocupa esas fechas"}', { status: 409 })));
        await expect(fetchJson("/api/v1/bff/reservations", "token")).rejects.toMatchObject({ message: "La unidad ocupa esas fechas", status: 409 });
    });
    it("no muestra información interna de un error 500", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response('{"detail":"SQL y credenciales internas"}', { status: 500 })));
        await expect(fetchJson("/api/v1/bff/reports", "token")).rejects.toMatchObject({ message: "El servicio no está disponible. Vuelve a intentarlo.", status: 500 });
    });
    it("acepta la respuesta 204 de una eliminación", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
        expect(await fetchJson("/api/v1/bff/catalog/1", "token", { method: "DELETE" })).toBe(null);
    });
    it("muestra el aviso 429 y conserva Retry-After sin repetir una reserva", async () => {
        const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ detail: "Espera 3 segundos y vuelve a intentarlo." }), {
            status: 429, headers: { "Retry-After": "3", "Content-Type": "application/problem+json" },
        }));
        vi.stubGlobal("fetch", fetch);
        await expect(fetchJson("/api/v1/bff/reservations", "token", { method: "POST", body: { unitId: 1 } }))
            .rejects.toMatchObject({ message: "Espera 3 segundos y vuelve a intentarlo.", status: 429, retryAfterSeconds: 3 });
        expect(fetch).toHaveBeenCalledTimes(1);
    });
    it.each([null, "", "incorrecto", "-1", "1.5", "99999999999999999999"])("tolera Retry-After inválido (%s) y una respuesta 429 sin JSON", async (retryAfter) => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("Too many requests", {
            status: 429, headers: retryAfter === null ? {} : { "Retry-After": retryAfter },
        })));
        const error = await fetchJson("/api/v1/bff/reports", "token").catch(error => error);
        expect(error).toMatchObject({ status: 429, message: "Se enviaron demasiadas solicitudes. Espera unos segundos y vuelve a intentarlo." });
        expect(error.retryAfterSeconds).toBeUndefined();
    });
});
