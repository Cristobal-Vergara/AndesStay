import { useCallback } from "react";
import Navbar from "../components/Navbar";
import KpiCards from "../components/KpiCards";
import PageHeader from "../components/PageHeader";
import EmptyState from "../components/EmptyState";
import Icon from "../components/Icon";
import { useApi } from "../hooks/useApi";
import { useResource } from "../hooks/useResource";

export default function Reports() {
    const { request, account, roles } = useApi();
    const load = useCallback(signal => request("/api/bff/reports", { signal }), [request]);
    const reports = useResource(load, !!account && roles.includes("Admin"));
    const data = reports.data;
    const hours = Object.entries(data?.reservasPorHora || {}).sort(([a], [b]) => Number(a) - Number(b));
    const max = Math.max(1, ...hours.map(([, count]) => Number(count)));
    return <><Navbar /><main className="app-main" id="main-content">
        <PageHeader eyebrow="INDICADORES & ACTIVIDAD" title="Reportes" description="Los números detrás de cada estadía.">
            <button type="button" onClick={reports.refresh} disabled={reports.loading}><Icon name="refresh" size={17} />Actualizar</button>
        </PageHeader>
        {reports.error && <p role="alert">{reports.error}</p>}
        {reports.loading && <p role="status" className="loading-state">Cargando indicadores…</p>}
        <KpiCards data={data} />
        {data && <>
            <p className="metric-note">La ocupación considera las unidades en estadía sobre todo el catálogo, incluidas las deshabilitadas.</p>
            <section className="panel hourly-panel"><div className="panel-heading"><div><p className="eyebrow">ACTIVIDAD HISTÓRICA</p><h2>Solicitudes por hora</h2></div><span className="subtle-tag">{data.timeZone}</span></div>
                <p className="muted">Hora de creación de las solicitudes, incluso si la reserva fue eliminada después.</p>
                {hours.length ? <div className="chart-scroll"><figure className="hour-chart" aria-label="Solicitudes históricas creadas por hora">
                    {hours.map(([hour, count]) => <div className="hour-column" key={hour} role="img" aria-label={`${String(hour).padStart(2, "0")}:00 · ${count} solicitudes históricas`}><span className="hour-value" aria-hidden="true">{count}</span>
                        <div className="hour-track"><div className="hour-bar" style={{ height: `${Number(count) / max * 100}%` }} /></div>
                        <span className="hour-label" aria-hidden="true">{String(hour).padStart(2, "0")}:00</span>
                    </div>)}
                </figure></div> : <EmptyState title="Todavía no hay actividad" description="Las solicitudes aparecerán aquí cuando se registren." icon="chart" />}
            </section>
            <div className="report-detail-grid">
                <section className="panel cycle-panel"><span className="panel-icon"><Icon name="clock" size={24} /></span><p className="eyebrow">TIEMPO A CONFIRMACIÓN</p>
                    <h2>De solicitud a bienvenida.</h2><p className="cycle-number">{Number(data.reservasConCiclo) > 0 ? Number(data.tiempoCicloPromedioMinutos || 0).toLocaleString("es-CL", { maximumFractionDigits: 2 }) : "—"}{Number(data.reservasConCiclo) > 0 && <span>minutos</span>}</p>
                    <p className="muted">{Number(data.reservasConCiclo) > 0 ? `Promedio histórico de ${data.reservasConCiclo} reservas con creación y confirmación válidas.` : "Todavía no hay reservas confirmadas con las que calcular un promedio."}</p>
                </section>
                <section className="panel"><div className="panel-heading"><h2>Reservas por unidad</h2><Icon name="cabin" /></div>
                    {Object.entries(data.reservasPorUnidad || {}).length ? <div className="table-scroll"><table><thead><tr><th>Unidad</th><th>Reservas actuales</th></tr></thead>
                        <tbody>{Object.entries(data.reservasPorUnidad).map(([id, count]) => <tr key={id}><td><strong>Unidad {id}</strong></td><td><span className="count-pill">{count}</span></td></tr>)}</tbody>
                    </table></div> : <EmptyState title="Sin reservas registradas" description="Aquí verás la distribución por unidad." icon="cabin" />}
                </section>
            </div>
        </>}
        <footer className="page-footer">AndesStay <span>Información para cuidar cada detalle.</span></footer>
    </main></>;
}
