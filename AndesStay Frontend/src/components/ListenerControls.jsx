import { useCallback, useEffect, useRef, useState } from "react";
import { useApi } from "../hooks/useApi";
import StatusBadge from "./StatusBadge";
import Icon from "./Icon";

const states = { RUNNING: ["Iniciado", "green"], STOPPING: ["Pausando…", "amber"], STOPPED: ["En pausa", "neutral"] };
const validListener = value => value && typeof value.id === "string" && typeof value.label === "string" && Object.hasOwn(states, value.state);

export default function ListenerControls({ canManage = false }) {
    const { request } = useApi();
    const [listeners, setListeners] = useState(null);
    const [error, setError] = useState("");
    const [feedback, setFeedback] = useState("");
    const [refreshing, setRefreshing] = useState(true);
    const [changing, setChanging] = useState("");
    const active = useRef(false);
    const flight = useRef(null);
    const version = useRef(0);

    const reload = useCallback(async () => {
        if (!active.current || flight.current) return;
        const current = { controller: new AbortController(), version: ++version.current };
        flight.current = current;
        setRefreshing(true);
        try {
            const result = await request("/api/v1/bff/messaging/listeners", { signal: current.controller.signal });
            if (!Array.isArray(result) || !result.every(validListener)) throw new Error("No se pudo verificar el estado de los consumidores.");
            if (active.current && current.version === version.current) { setListeners(result); setError(""); }
        } catch (cause) {
            if (active.current && current.version === version.current && !current.controller.signal.aborted) {
                setListeners(null); setError(cause.message);
            }
        } finally {
            if (flight.current === current) flight.current = null;
            if (active.current && current.version === version.current) setRefreshing(false);
        }
    }, [request]);

    const cancel = useCallback(() => {
        ++version.current;
        flight.current?.controller.abort(); flight.current = null;
    }, []);

    useEffect(() => {
        active.current = true;
        queueMicrotask(() => { if (active.current) reload(); });
        const timer = setInterval(reload, 10000);
        return () => {
            active.current = false;
            clearInterval(timer); cancel();
        };
    }, [reload, cancel]);

    const change = async (listener, action) => {
        if (flight.current?.action) return;
        flight.current?.controller.abort();
        const current = { controller: new AbortController(), version: ++version.current, action };
        flight.current = current;
        setChanging(listener.id); setRefreshing(false); setFeedback(""); setError("");
        try {
            const result = await request(`/api/v1/bff/messaging/listeners/${encodeURIComponent(listener.id)}/${action}`, {
                method: "POST", signal: current.controller.signal,
            });
            if (!validListener(result) || result.id !== listener.id) throw new Error("No se pudo verificar el cambio del consumidor.");
            if (active.current && current.version === version.current) {
                setListeners(previous => previous?.map(item => item.id === result.id ? result : item));
                setFeedback(`${result.label}: ${states[result.state][0].toLowerCase()}.`);
            }
        } catch (cause) {
            if (active.current && current.version === version.current && !current.controller.signal.aborted) {
                setListeners(null); setError(cause.message);
            }
        } finally {
            if (flight.current === current) flight.current = null;
            if (active.current && current.version === version.current) setChanging("");
        }
    };

    return <section className="dashboard-section" aria-labelledby="listeners-heading">
        <div className="section-heading">
            <div><h2 id="listeners-heading">Control de avisos</h2><p className="muted">Consumidores del backend · Se actualiza cada 10 segundos.</p></div>
            <button type="button" onClick={reload} disabled={refreshing || Boolean(changing)}><Icon name="refresh" size={16} />Actualizar consumidores</button>
        </div>
        <div className="panel listener-panel">
            <p className="muted">Al pausar, terminan los avisos ya recibidos. Los nuevos esperan en la cola según su retención. La pausa se restablece al reiniciar el backend.</p>
            {error && <p role="alert">{error}</p>}
            {!listeners && !error && <p role="status">Consultando consumidores…</p>}
            {listeners?.length === 0 && <p>No hay consumidores habilitados.</p>}
            {listeners?.map(listener => <div className="listener-row" key={listener.id}>
                <div className="listener-name"><strong>{listener.label}</strong><StatusBadge tone={states[listener.state][1]}>{states[listener.state][0]}</StatusBadge></div>
                {canManage && <button type="button" disabled={Boolean(changing) || listener.state === "STOPPING"}
                    aria-label={`${listener.state === "STOPPED" ? "Reanudar" : "Pausar"} ${listener.label.toLowerCase()}`}
                    onClick={() => change(listener, listener.state === "STOPPED" ? "resume" : "pause")}>
                    {changing === listener.id ? "Aplicando…" : listener.state === "STOPPED" ? "Reanudar" : "Pausar"}
                </button>}
            </div>)}
            {feedback && <p role="status">{feedback}</p>}
            <p className="muted">«Iniciado» indica el estado del consumidor. Comprueba la conexión a RabbitMQ en Entrega de avisos. {canManage ? "Solo Admin puede pausar y reanudar." : "Tu cuenta tiene acceso de consulta."}</p>
        </div>
    </section>;
}
