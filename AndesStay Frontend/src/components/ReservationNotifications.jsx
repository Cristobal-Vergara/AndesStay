import { useCallback, useState } from "react";
import { useApi } from "../hooks/useApi";
import { useResource } from "../hooks/useResource";
import { queryPath, requirePage } from "../api";
import { TIME_ZONE } from "../format";
import EmptyState from "./EmptyState";
import Icon from "./Icon";
import Pagination from "./Pagination";
import StatusBadge from "./StatusBadge";

const dateFormat = new Intl.DateTimeFormat("es-CL", {
    timeZone: TIME_ZONE, dateStyle: "medium", timeStyle: "short",
});

export default function ReservationNotifications({ staff = false }) {
    const { request } = useApi();
    const [page, setPage] = useState(0);
    const load = useCallback(signal => request(queryPath("/api/v1/bff/notifications", { page, size: 5 }), { signal }).then(requirePage), [request, page]);
    const notices = useResource(load);
    return <section className="dashboard-section" aria-labelledby="notification-heading">
        <div className="section-heading">
            <div><h2 id="notification-heading">{staff ? "Actividad de reservas" : "Mis avisos"}</h2>
                <p className="muted">{staff ? "Nuevas solicitudes y cambios de confirmación." : "Las novedades de tus reservas, en un solo lugar."}</p></div>
            <button type="button" onClick={notices.refresh} disabled={notices.loading}><Icon name="refresh" size={16} />Actualizar avisos</button>
        </div>
        <div className="panel">
            {notices.loading && <p role="status" className="loading-state">Cargando avisos…</p>}
            {notices.error && <p role="alert">{notices.error}</p>}
            {notices.data?.content.length === 0 && <EmptyState title="Todo al día"
                description={staff ? "Los próximos movimientos de reservas aparecerán aquí." : "Aquí aparecerán las confirmaciones y cancelaciones de tus reservas."} icon="check" />}
            {!!notices.data?.content.length && <ul className="notification-list">{notices.data.content.map(notice => <li key={notice.id}>
                <span className="panel-icon"><Icon name={notice.status === "CANCELADA" ? "close" : notice.status === "CONFIRMADA" ? "check" : "calendar"} /></span>
                <div className="notification-content"><h3>{notice.title}</h3><p>Reserva #{notice.reservationId} · Unidad {notice.unitId}</p>
                    <time dateTime={notice.occurredAt}>{dateFormat.format(new Date(notice.occurredAt))}</time></div>
                <StatusBadge status={notice.status} />
            </li>)}</ul>}
            <Pagination data={notices.data} page={page} onChange={setPage} loading={notices.loading} label="avisos" />
        </div>
    </section>;
}
