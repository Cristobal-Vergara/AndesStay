import { useCallback, useEffect, useRef, useState } from "react";
import { useApi } from "../hooks/useApi";
import Icon from "./Icon";

export default function MessagingHealth({ canDemo = false }) {
    const { request } = useApi();
    const [status, setStatus] = useState(null);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(true);
    const [sending, setSending] = useState(false);
    const [feedback, setFeedback] = useState("");
    const active = useRef(false);
    const busy = useRef(false);
    const controller = useRef(null);
    const reload = useCallback(async () => {
        if (busy.current) return;
        busy.current = true;
        const current = new AbortController();
        controller.current = current;
        try {
            const result = await request("/api/v1/bff/messaging/status", { signal: current.signal });
            if (active.current && !current.signal.aborted) { setStatus(result); setError(""); }
        } catch (cause) {
            if (active.current && !current.signal.aborted && cause.name !== "AbortError") { setError(cause.message); setStatus(null); }
        } finally {
            if (controller.current === current) busy.current = false;
            if (active.current && !current.signal.aborted) setLoading(false);
        }
    }, [request]);
    useEffect(() => {
        active.current = true;
        queueMicrotask(() => { if (active.current) reload(); });
        const timer = setInterval(reload, 10000);
        return () => { active.current = false; clearInterval(timer); controller.current?.abort(); busy.current = false; };
    }, [reload]);
    const demo = async () => {
        setSending(true); setFeedback("");
        try {
            await request("/api/v1/bff/messaging/demo", { method: "POST" });
            if (active.current) setFeedback("Prueba enviada. La alerta aparecerá después de los tres intentos; se limpia automáticamente al minuto.");
            await reload();
        } catch (cause) {
            if (active.current) setFeedback(cause.message);
        } finally { if (active.current) setSending(false); }
    };
    const available = status?.available && !error;
    const failed = available && status.quarantined > 0;
    const stopped = available && (status.receptionConsumers === 0 || status.guestConsumers === 0);
    return <section className="dashboard-section" aria-labelledby="delivery-heading">
        <div className="section-heading">
            <div><h2 id="delivery-heading">Entrega de avisos</h2><p className="muted">Estado de las colas · Se actualiza cada 10 segundos.</p></div>
            <button type="button" onClick={reload} disabled={loading}><Icon name="refresh" size={16} />Actualizar estado</button>
        </div>
        <div className="panel delivery-panel">
            <div className="delivery-summary"><span className="panel-icon"><Icon name="shield" /></span>
                <div><h3>{loading ? "Consultando entrega…" : !available ? "Estado sin verificar" : failed ? "Hay avisos por revisar" : stopped ? "Entrega en pausa" : "Entrega activa"}</h3>
                    <p className="muted">{!available ? "No hay una lectura disponible del servicio de mensajería." : failed ? "Los avisos fallidos están en cuarentena para revisión." : stopped ? "Una de las colas no tiene consumidores activos." : "Los consumidores están disponibles para procesar nuevos avisos."}</p></div>
            </div>
            {error && <p role="alert">{error}</p>}
            {available && <div className="delivery-counts">
                <div><strong>{status.pending}</strong><span>En espera</span></div>
                <div className={failed ? "delivery-warning" : ""}><strong>{status.quarantined}</strong><span>En cuarentena</span></div>
            </div>}
            {failed && <p className="delivery-warning" role="alert">Revisa la cola andesstay.reservations.dlq en RabbitMQ antes de que venza la retención de 7 días.</p>}
            {stopped && <p role="alert">Revisa los consumidores del backend para reanudar la entrega.</p>}
            {available && status.demoEnabled && <div className="delivery-demo">
                <div><strong>Prueba local</strong><p className="muted">Provoca un fallo en una cola separada. Tus reservas y avisos permanecen intactos.</p></div>
                {canDemo && <button type="button" onClick={demo} disabled={sending}>{sending ? "Enviando…" : "Probar alerta"}</button>}
                {status.demoQuarantined > 0 && <p role="status" className="delivery-warning">Prueba local: {status.demoQuarantined} mensaje(s) en cuarentena. La retención de prueba es de 60 segundos.</p>}
            </div>}
            {feedback && <p role="status">{feedback}</p>}
        </div>
    </section>;
}
