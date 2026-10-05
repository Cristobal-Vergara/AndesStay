import PageHeader from "../components/PageHeader";
import EmptyState from "../components/EmptyState";
import Icon from "../components/Icon";
import { useCallback, useState } from "react";
import Navbar from "../components/Navbar";
import Pagination from "../components/Pagination";
import { useApi } from "../hooks/useApi";
import { useResource } from "../hooks/useResource";
import { queryPath, requirePage } from "../api";
import { localDateTime, TIME_ZONE } from "../format";

const blank = { user: "", action: "", inicio: "", fin: "" };
const actions = {
    RESERVA_CREADA: "Reserva creada", RESERVA_CONFIRMADA: "Reserva confirmada",
    RESERVA_CANCELADA: "Reserva cancelada", RESERVA_ACTUALIZADA: "Reserva actualizada",
    RESERVA_ELIMINADA: "Reserva eliminada", PROPIETARIO_ASIGNADO: "Cuenta de huésped asignada",
    CHECKIN_PENDIENTE: "Check-in pendiente", CHECKIN: "Check-in", CHECKOUT: "Check-out",
};
export default function Audit() {
    const { request, account } = useApi();
    const [fields, setFields] = useState(blank);
    const [applied, setApplied] = useState(blank);
    const [page, setPage] = useState(0);
    const [error, setError] = useState("");
    const load = useCallback(signal => request(queryPath("/api/bff/audit", { ...applied, page, size: 20 }), { signal }).then(requirePage),
        [request, applied, page]);
    const events = useResource(load, !!account);
    const change = event => setFields(current => ({ ...current, [event.target.name]: event.target.value }));
    const filter = event => {
        event.preventDefault();
        if (fields.inicio && fields.fin && fields.inicio > fields.fin) {
            setError("La fecha de inicio no puede ser posterior a la fecha de fin."); return;
        }
        setError(""); setPage(0); setApplied({ ...fields });
    };
    const clear = () => { setFields(blank); setApplied({ ...blank }); setPage(0); setError(""); };
    return <><Navbar /><main className="app-main" id="main-content">
        <PageHeader eyebrow="HISTORIAL & TRAZABILIDAD" title="Auditoría" description="Cada movimiento de las reservas, en un solo lugar.">
            <span className="subtle-tag"><Icon name="clock" size={13} />{TIME_ZONE}</span>
        </PageHeader>
        <form onSubmit={filter}><div className="panel-heading"><span className="panel-icon"><Icon name="filter" size={18} /></span><h2>Encuentra un evento</h2></div><div className="form-grid">
            <label>Usuario<input name="user" value={fields.user} onChange={change} placeholder="Usuario exacto" /></label>
            <label>Tipo de evento<select name="action" value={fields.action} onChange={change}>
                <option value="">Todos</option>
                {Object.entries(actions).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select></label>
            <label>Desde<input name="inicio" type="datetime-local" step="1" value={fields.inicio} onChange={change} /></label>
            <label>Hasta<input name="fin" type="datetime-local" step="1" value={fields.fin} onChange={change} /></label>
        </div><div className="actions">
            <button type="submit" disabled={events.loading}><Icon name="filter" size={16} />Filtrar</button>
            <button type="button" onClick={clear} disabled={events.loading}>Limpiar filtros</button>
        </div></form>
        {error || events.error ? <p role="alert">{error || events.error}</p> : null}
        {events.loading && <p role="status" className="loading-state">Cargando eventos…</p>}
        <div className="section-heading"><h2>Actividad de las reservas</h2><span className="subtle-tag">{events.data?.totalElements ?? 0} eventos encontrados</span></div>
        <div className="table-scroll" tabIndex={0} role="region" aria-label="Historial de auditoría"><table><thead><tr>
            <th>Evento</th><th>Acción</th><th>Reserva</th><th>Usuario</th><th>Descripción</th><th>Fecha y hora</th>
        </tr></thead><tbody>
            {events.data?.content.map(event => <tr key={event.id}>
                <td className="table-id">#{event.id}</td><td><strong>{actions[event.action] || event.action}</strong></td><td className="table-id">#{event.reservationId}</td>
                <td className="table-user">{event.user}</td><td>{event.description}</td><td className="table-date">{localDateTime(event.eventDate)}</td>
            </tr>)}
            {events.data?.content.length === 0 && <tr><td colSpan={6}><EmptyState title="No se encontraron eventos" description="Prueba con otros filtros o revisa el historial completo." icon="shield" /></td></tr>}
        </tbody></table></div>
        <Pagination data={events.data} page={page} onChange={setPage} loading={events.loading} label="eventos" />
        <footer className="page-footer">AndesStay <span>Los detalles también cuentan una historia.</span></footer>
    </main></>;
}
