import PageHeader from "../components/PageHeader";
import StatusBadge from "../components/StatusBadge";
import EmptyState from "../components/EmptyState";
import Icon from "../components/Icon";
import { useCallback, useEffect, useRef, useState } from "react";
import Navbar from "../components/Navbar";
import Pagination from "../components/Pagination";
import { useApi } from "../hooks/useApi";
import { useResource } from "../hooks/useResource";
import { queryPath, requirePage } from "../api";
import { localDate, stateLabels, transitions, money } from "../format";

const blankForm = { guestName: "", unitId: "", checkIn: "", checkOut: "" };
export default function Reservations() {
    const { request, account, roles } = useApi();
    const staff = roles.includes("Admin") || roles.includes("Recepcionista");
    const [page, setPage] = useState(0);
    const [unitPage, setUnitPage] = useState(0);
    const [form, setForm] = useState(blankForm);
    const [selectedUnit, setSelectedUnit] = useState(null);
    const [editing, setEditing] = useState(null);
    const [assigning, setAssigning] = useState(null);
    const formRef = useRef(null);
    const assignmentRef = useRef(null);
    const [objectId, setObjectId] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const load = useCallback(signal => request(queryPath("/api/bff/reservations", { page, size: 20 }), { signal }).then(requirePage), [request, page]);
    const loadUnits = useCallback(signal => request(queryPath("/api/bff/catalog", { page: unitPage, size: 20 }), { signal }).then(requirePage), [request, unitPage]);
    const loadAccount = useCallback(signal => request("/api/bff/account", { signal }), [request]);
    const reservations = useResource(load, !!account);
    const units = useResource(loadUnits, !!account);
    const identity = useResource(loadAccount, !!account);
    useEffect(() => {
        if (editing === null) return;
        formRef.current?.scrollIntoView?.({ behavior: "smooth", block: "start" });
        formRef.current?.querySelector("input")?.focus({ preventScroll: true });
    }, [editing]);
    useEffect(() => {
        if (assigning === null) return;
        assignmentRef.current?.scrollIntoView?.({ behavior: "smooth", block: "start" });
        assignmentRef.current?.querySelector("input")?.focus({ preventScroll: true });
    }, [assigning]);
    const options = units.data?.content.filter(unit => unit.available) || [];
    if (selectedUnit && !options.some(unit => unit.id === selectedUnit.id)) options.unshift(selectedUnit);

    const change = event => setForm(current => ({ ...current, [event.target.name]: event.target.value }));
    const chooseUnit = event => {
        const id = event.target.value;
        setForm(current => ({ ...current, unitId: id }));
        setSelectedUnit(units.data?.content.find(unit => String(unit.id) === id) || null);
    };
    const cancel = () => { setEditing(null); setForm(blankForm); setSelectedUnit(null); };
    const perform = async (operation, success) => {
        if (busy) return false;
        setBusy(true); setError(""); setMessage("");
        try { await operation(); setMessage(success); reservations.refresh(); return true; }
        catch (failure) { setError(failure.message); return false; }
        finally { setBusy(false); }
    };
    const save = async event => {
        event.preventDefault();
        if (!form.guestName.trim() || !form.unitId || !form.checkIn || !form.checkOut || form.checkOut <= form.checkIn) {
            setError("Completa los datos y elige una salida posterior a la entrada."); return;
        }
        if (await perform(() => request(editing ? `/api/bff/reservations/${editing}` : "/api/bff/reservations",
                { method: editing ? "PUT" : "POST", body: { ...form, guestName: form.guestName.trim(), unitId: Number(form.unitId) } }),
                editing ? "Reserva actualizada." : "Solicitud creada. Recepción debe confirmar la disponibilidad.")) cancel();
    };
    const edit = reservation => {
        setEditing(reservation.id);
        setForm({ guestName: reservation.guestName, unitId: String(reservation.unitId),
            checkIn: reservation.checkIn, checkOut: reservation.checkOut });
        setSelectedUnit(units.data?.content.find(unit => unit.id === reservation.unitId)
            || { id: reservation.unitId, name: `Unidad ${reservation.unitId}`, available: true });
        setError(""); setMessage("");
    };
    const remove = async reservation => {
        if (!window.confirm(`¿Eliminar la reserva ${reservation.id}? Su evento de eliminación quedará en auditoría.`)) return;
        if (await perform(() => request(`/api/bff/reservations/${reservation.id}`, { method: "DELETE" }), "Reserva eliminada.")) {
            if (reservations.data?.content.length === 1 && page > 0) setPage(page - 1);
            if (editing === reservation.id) cancel();
        }
    };
    const assign = async event => {
        event.preventDefault();
        if (await perform(() => request(`/api/bff/reservations/${assigning}` + "/owner",
                { method: "PUT", body: { objectId: objectId.trim() } }), "Reserva asignada a la cuenta del huésped.")) {
            setAssigning(null); setObjectId("");
        }
    };
    const copyIdentity = async () => {
        try { await navigator.clipboard.writeText(identity.data.objectId); setMessage("Identificador copiado."); }
        catch { setError("No se pudo copiar. Puedes seleccionar y copiar el identificador mostrado."); }
    };
    return <><Navbar /><main className="app-main" id="main-content">
        <PageHeader eyebrow="SOLICITUDES & ESTADÍAS" title={staff ? "Reservas" : "Mis reservas"}
            description={staff ? "Organiza cada llegada, desde la solicitud hasta la despedida." : "Planifica tu estadía y consulta tus próximas reservas."}>
            {reservations.data && <span className="subtle-tag">{reservations.data.totalElements} reservas registradas</span>}
        </PageHeader>
        {!staff && identity.data && <section className="panel identity-panel">
            <div><p>Identificador de mi cuenta para recepción <code>{identity.data.objectId}</code></p>
                <small>Compártelo con recepción para vincular a tu cuenta una reserva creada allí.</small></div>
            <button type="button" onClick={copyIdentity}>Copiar identificador</button>
        </section>}
        {error || reservations.error || units.error || identity.error
            ? <p role="alert">{error || reservations.error || units.error || identity.error}</p> : null}
        {message && <p role="status" className="notice">{message}</p>}
        <form ref={formRef} onSubmit={save} id="reservation-form"><h2>{editing ? `Editar reserva ${editing}` : "Crear solicitud de reserva"}</h2>
            <p className="form-intro">Elige el alojamiento y las fechas. Recepción confirmará la disponibilidad.</p>
            <fieldset disabled={busy}><div className="form-grid">
                <label>Nombre del huésped<input name="guestName" value={form.guestName} onChange={change} maxLength={255} placeholder="Nombre y apellido" required /></label>
                <label>Unidad<select name="unitId" value={form.unitId} onChange={chooseUnit} required disabled={units.loading}>
                    <option value="">Selecciona una unidad</option>
                    {options.map(unit => <option key={unit.id} value={unit.id}>{unit.name}{unit.price ? ` · ${money(unit.price)}/noche` : ""}</option>)}
                </select></label>
                <label>Entrada<input name="checkIn" type="date" value={form.checkIn} onChange={change} required /></label>
                <label>Salida<input name="checkOut" type="date" value={form.checkOut} onChange={change} min={form.checkIn || undefined} required /></label>
            </div>
            <Pagination data={units.data} page={unitPage} onChange={setUnitPage} loading={units.loading} label="opciones de unidad" />
            <div className="actions"><button type="submit" disabled={units.loading || !form.unitId}><Icon name={editing ? "check" : "plus"} size={17} />{editing ? "Guardar cambios" : "Crear solicitud"}</button>
                {editing && <button type="button" onClick={cancel}>Cancelar edición</button>}
            </div></fieldset>
        </form>
        {staff && assigning !== null && <form ref={assignmentRef} onSubmit={assign}><h2>Asignar reserva {assigning} a un huésped</h2>
            <label>Identificador de cuenta del huésped<input value={objectId} onChange={event => setObjectId(event.target.value)}
                pattern="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}" maxLength={36} required /></label>
            <p className="muted">Usa el identificador que el huésped comparte desde “Mis reservas”. Su cuenta debe pertenecer a la misma organización.</p>
            <div className="actions"><button disabled={busy} type="submit">Asignar cuenta</button>
                <button disabled={busy} type="button" onClick={() => setAssigning(null)}>Cancelar asignación</button></div>
        </form>}
        <div className="section-heading"><h2>Reservas existentes</h2><Icon name="calendar" /></div>
        {reservations.loading && <p role="status" className="loading-state">Cargando reservas…</p>}
        <div className="table-scroll" tabIndex={0} role="region" aria-label="Listado de reservas"><table><thead><tr>
            <th>Reserva</th><th>Huésped</th><th>Unidad</th><th>Entrada</th><th>Salida</th><th>Estado</th>{staff && <th>Gestión</th>}
        </tr></thead><tbody>
            {reservations.data?.content.map(reservation => <tr key={reservation.id}>
                <td className="table-id">#{reservation.id}</td><td><strong>{reservation.guestName}</strong></td><td className="table-id">#{reservation.unitId}</td>
                <td className="table-date">{localDate(reservation.checkIn)}</td><td className="table-date">{localDate(reservation.checkOut)}</td>
                <td><StatusBadge status={reservation.status} /></td>
                {staff && <td>
                    {!!transitions[reservation.status]?.length && <select aria-label={`Cambiar estado de reserva ${reservation.id}`} value="" disabled={busy}
                        onChange={event => event.target.value && perform(() => request(`/api/bff/reservations/${reservation.id}/status`,
                            { method: "PUT", body: { status: event.target.value } }), "Estado actualizado.")}>
                        <option value="">Cambiar estado…</option>
                        {transitions[reservation.status].map(state => <option key={state} value={state}>{stateLabels[state]}</option>)}
                    </select>}
                    <small>{reservation.guestAssigned ? "Cuenta vinculada" : "Cuenta pendiente de vincular"}</small>
                    <div className="actions">
                        <button type="button" disabled={busy} onClick={() => edit(reservation)}>Editar</button>
                        <button type="button" disabled={busy} onClick={() => { setAssigning(reservation.id); setObjectId(""); }}>Asignar huésped</button>
                        <button type="button" className="button-danger" disabled={busy} onClick={() => remove(reservation)}>Eliminar</button>
                    </div>
                </td>}
            </tr>)}
            {reservations.data?.content.length === 0 && <tr><td colSpan={staff ? 7 : 6}><EmptyState title="No hay reservas para mostrar" description="Crea una solicitud para comenzar a planificar una estadía." /></td></tr>}
        </tbody></table></div>
        <Pagination data={reservations.data} page={page} onChange={setPage} loading={reservations.loading || busy} label="reservas" />
        <footer className="page-footer">AndesStay <span>Cada bienvenida empieza aquí.</span></footer>
    </main></>;
}
