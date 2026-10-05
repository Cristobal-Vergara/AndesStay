export const TIME_ZONE = import.meta.env?.VITE_APP_TIME_ZONE || "America/Santiago";
export const money = (value) => new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 2 }).format(value);
export const localDate = (value) => value ? value.split("-").reverse().join("/") : "—";
export const localDateTime = (value) => value ? value.replace("T", " ").slice(0, 19) : "—";
export const stateLabels = {
    CREADA: "Solicitud creada", CONFIRMADA: "Confirmada", CANCELADA: "Cancelada",
    CHECKIN_PENDIENTE: "Check-in pendiente", "EN_ESTADÍA": "En estadía", CHECKOUT: "Finalizada",
};
export const transitions = {
    CREADA: ["CONFIRMADA", "CANCELADA"],
    CONFIRMADA: ["CHECKIN_PENDIENTE", "CANCELADA"],
    CHECKIN_PENDIENTE: ["EN_ESTADÍA"],
    "EN_ESTADÍA": ["CHECKOUT"],
};
