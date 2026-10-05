import Icon from "./Icon";
export default function KpiCards({ data }) {
    if (!data) return null;
    const cards = [
        { label: "Ocupación del catálogo", value: `${Number(data.ocupacionPorcentaje || 0).toLocaleString("es-CL", { maximumFractionDigits: 2 })}%`, detail: `${data.ocupacionActiva ?? 0} ${Number(data.ocupacionActiva) === 1 ? "unidad" : "unidades"} en estadía`, icon: "chart", featured: true },
        { label: "Reservas registradas", value: data.totalReservas, detail: "Total de reservas actuales", icon: "calendar" },
        { label: "Solicitudes pendientes", value: data.creadas, detail: "Por revisar y confirmar", icon: "clock" },
        { label: "Unidades habilitadas", value: data.unidadesHabilitadas, detail: `De ${data.totalUnidades ?? 0} unidades en catálogo`, icon: "cabin" },
    ];
    const states = [["Confirmadas", data.confirmadas], ["Check-in pendiente", data.checkinPendiente],
        ["Reservas en estadía", data.enEstadia], ["Finalizadas", data.checkout], ["Canceladas", data.canceladas]];
    return <><div className="kpi-grid">{cards.map(card => <article key={card.label} className={`kpi-card ${card.featured ? "kpi-featured" : ""}`}>
        <div className="kpi-top"><span>{card.label}</span><span className="kpi-icon"><Icon name={card.icon} /></span></div>
        <strong className="kpi-value">{card.value ?? 0}</strong><small>{card.detail}</small>
    </article>)}</div>
        <div className="reservation-summary" aria-label="Resumen por estado de reserva">{states.map(([label, value]) =>
            <div key={label}><span>{label}</span><strong>{value ?? 0}</strong></div>)}</div>
    </>;
}
