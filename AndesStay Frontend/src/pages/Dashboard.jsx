import { useCallback, useState } from "react";
import { Link } from "react-router-dom";
import Navbar from "../components/Navbar";
import MessagingHealth from "../components/MessagingHealth";
import ListenerControls from "../components/ListenerControls";
import ReservationNotifications from "../components/ReservationNotifications";
import KpiCards from "../components/KpiCards";
import PageHeader from "../components/PageHeader";
import StatusBadge from "../components/StatusBadge";
import EmptyState from "../components/EmptyState";
import Icon from "../components/Icon";
import { useApi } from "../hooks/useApi";
import { useResource } from "../hooks/useResource";
import { queryPath, requirePage } from "../api";
import { localDate } from "../format";

export default function Dashboard() {
    const { request, account, roles } = useApi();
    const admin = roles.includes("Admin");
    const staff = admin || roles.includes("Recepcionista");
    const guest = roles.includes("Huesped") && !staff;
    const auditor = roles.includes("Auditor");
    const [date, setDate] = useState("");
    const loadKpis = useCallback(signal => request("/api/v1/bff/reports", { signal }), [request]);
    const loadDaily = useCallback(signal => request(queryPath("/api/v1/bff/reports/operations", { date }), { signal }), [request, date]);
    const loadOwn = useCallback(signal => request(queryPath("/api/v1/bff/reservations", { page: 0, size: 5 }), { signal }).then(requirePage), [request]);
    const kpis = useResource(loadKpis, !!account && admin);
    const daily = useResource(loadDaily, !!account && staff);
    const own = useResource(loadOwn, !!account && guest);
    const firstName = account?.name?.split(" ")[0];
    return <><Navbar /><main className="app-main" id="main-content">
        <PageHeader eyebrow="BIENVENIDO A TU ESPACIO" title={firstName ? `Hola, ${firstName}.` : "Bienvenido a AndesStay."}
            description={staff ? "Cada detalle cuenta para una gran estadía." : "Tu próxima escapada empieza aquí."}>
            {(staff || guest) && <Link className="button button-primary" to="/reservations"><Icon name="calendar" />{staff ? "Gestionar reservas" : "Mis reservas"}</Link>}
        </PageHeader>
        <section className="dashboard-hero">
            <div><p className="eyebrow">EL ENCANTO DE HOSPEDAR</p><h2>{staff ? <>Todo listo<br />para recibir.</> : <>Encuentra tu<br />próximo respiro.</>}</h2>
                <p>{staff ? "Un espacio para organizar tus reservas y cuidar cada bienvenida." : "Descubre alojamientos y planifica una estadía a tu ritmo."}</p>
                {(staff || guest) && <Link to="/catalog" className="text-link">Explorar el catálogo <Icon name="arrow" size={17} /></Link>}
            </div><div className="hero-landscape" aria-hidden="true" />
        </section>
        {kpis.error || daily.error || own.error ? <p role="alert">{kpis.error || daily.error || own.error}</p> : null}
        {admin && <section className="dashboard-section"><div className="section-heading"><h2>Una mirada a AndesStay</h2><Link className="text-link" to="/reports">Ver reportes <Icon name="arrow" size={16} /></Link></div>
            {kpis.loading && <p role="status" className="loading-state">Cargando indicadores…</p>}<KpiCards data={kpis.data} /></section>}
        {staff && <section className="dashboard-section"><div className="section-heading"><div><h2>Operaciones del día</h2>
            <p className="muted">{daily.data ? `${localDate(daily.data.fecha)} · ${daily.data.timeZone}` : "Organiza las próximas llegadas y salidas."}</p></div>
            <label className="date-filter">Fecha<input type="date" value={date || daily.data?.fecha || ""} onChange={event => setDate(event.target.value)} /></label></div>
            {daily.loading && <p role="status" className="loading-state">Cargando llegadas y salidas…</p>}
            {daily.data && <div className="operations-grid">
                {[["Llegadas pendientes", daily.data.llegadas, "arrow"], ["Salidas pendientes", daily.data.salidas, "logout"]].map(([label, list, icon]) => <section key={label} className="panel operations-panel">
                    <div className="panel-heading"><span className="panel-icon"><Icon name={icon} /></span><h3>{label}</h3><span className="count-pill">{list.length}</span></div>
                    {list.length ? <ul className="operation-list">{list.map(reservation => <li key={reservation.id}>
                        <span className="guest-avatar">{reservation.guestName?.charAt(0).toUpperCase()}</span><div><strong>{reservation.guestName}</strong>
                            <small>Reserva #{reservation.id} · Unidad {reservation.unitId}</small><StatusBadge status={reservation.status} /></div>
                    </li>)}</ul> : <EmptyState title="Todo al día" description="No hay operaciones pendientes para esta fecha." icon={icon} />}
                </section>)}
            </div>}
        </section>}
        {guest && <section className="dashboard-section"><div className="section-heading"><h2>Mis últimas reservas</h2><Link className="text-link" to="/reservations">Ver todas <Icon name="arrow" size={16} /></Link></div>
            {own.loading && <p role="status" className="loading-state">Cargando tus reservas…</p>}
            <div className="stay-grid">{own.data?.content.map(reservation => <article className="panel stay-card" key={reservation.id}>
                <div className="panel-heading"><Icon name="cabin" /><strong>Unidad {reservation.unitId}</strong><StatusBadge status={reservation.status} /></div>
                <p className="stay-dates">{localDate(reservation.checkIn)} <span>→</span> {localDate(reservation.checkOut)}</p><small>Reserva #{reservation.id}</small>
            </article>)}</div>
            {own.data?.content.length === 0 && <div className="panel"><EmptyState title="Tu próxima estadía te espera" description="Todavía no tienes reservas asociadas. Explora el catálogo para crear una solicitud." icon="cabin" /></div>}
        </section>}
        {(staff || guest) && <ReservationNotifications staff={staff} />}
        {staff && <MessagingHealth canDemo={admin} />}
        {staff && <ListenerControls canManage={admin} />}
        {auditor && <Link className="panel audit-shortcut" to="/audit"><span className="panel-icon"><Icon name="shield" /></span><div><strong>Historial de auditoría</strong><p>Consulta y filtra los movimientos de las reservas.</p></div><Icon name="arrow" /></Link>}
        {!staff && !guest && !auditor && <div className="panel"><EmptyState title="Tu cuenta necesita un rol" description="Solicita al administrador de la organización un rol de AndesStay." icon="shield" /></div>}
        <footer className="page-footer">AndesStay <span>Una buena estadía empieza con los detalles.</span></footer>
    </main></>;
}
