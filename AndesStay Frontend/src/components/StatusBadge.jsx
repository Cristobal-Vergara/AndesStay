import { stateLabels } from "../format";
const colors = { CREADA: "amber", CONFIRMADA: "green", CANCELADA: "clay", CHECKIN_PENDIENTE: "amber", "EN_ESTADÍA": "green", CHECKOUT: "neutral" };
export default function StatusBadge({ status, children, tone }) {
    return <span className={`status-badge status-${tone || colors[status] || "neutral"}`}>
        <span className="status-dot" aria-hidden="true" />{children || stateLabels[status] || status}
    </span>;
}
