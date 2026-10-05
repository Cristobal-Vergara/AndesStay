export const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL || "").replace(/\/$/, "");

export function queryPath(path, parameters = {}) {
    const query = new URLSearchParams();
    for (const [name, value] of Object.entries(parameters)) {
        if (value !== null && value !== undefined && String(value).trim()) query.set(name, String(value).trim());
    }
    return path + (query.size ? `?${query}` : "");
}

export async function fetchJson(path, token, options = {}) {
    const controller = new AbortController();
    const abort = () => controller.abort();
    if (options.signal?.aborted) abort();
    options.signal?.addEventListener("abort", abort, { once: true });
    const timeout = setTimeout(abort, 20000);
    try {
        const response = await fetch(API_BASE_URL + path, {
            method: options.method || "GET",
            headers: {
                Authorization: `Bearer ${token}`,
                ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
            },
            body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
            signal: controller.signal,
        });
        if (!response.ok) {
            const problem = await response.json().catch(() => null);
            const fallback = response.status === 401 ? "Tu sesión no es válida. Vuelve a iniciar sesión."
                : response.status === 403 ? "No tienes permiso para realizar esta operación."
                : response.status >= 500 ? "El servicio no está disponible. Vuelve a intentarlo."
                : "No se pudo completar la solicitud.";
            const detail = response.status < 500 && typeof problem?.detail === "string" ? problem.detail : fallback;
            const error = new Error(detail);
            error.status = response.status;
            throw error;
        }
        if (response.status === 204) return null;
        return await response.json();
    } catch (error) {
        if (error.status) throw error;
        if (options.signal?.aborted) throw error;
        throw new Error(controller.signal.aborted
            ? "La solicitud tardó demasiado. Vuelve a intentarlo."
            : "No se pudo conectar con AndesStay. Vuelve a intentarlo.", { cause: error });
    } finally {
        clearTimeout(timeout);
        options.signal?.removeEventListener("abort", abort);
    }
}

export function requirePage(data) {
    if (!data || !Array.isArray(data.content) || !Number.isInteger(data.totalPages)) {
        throw new Error("La respuesta de AndesStay no es válida.");
    }
    return data;
}
