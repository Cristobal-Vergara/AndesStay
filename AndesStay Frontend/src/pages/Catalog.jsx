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
import { money } from "../format";

const emptyForm = { name: "", type: "", capacity: "1", price: "", available: true, description: "" };
export default function Catalog() {
    const { request, account, roles } = useApi();
    const staff = roles.includes("Admin") || roles.includes("Recepcionista");
    const [page, setPage] = useState(0);
    const [form, setForm] = useState(emptyForm);
    const [editing, setEditing] = useState(null);
    const [showForm, setShowForm] = useState(false);
    const formRef = useRef(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const load = useCallback(signal => request(queryPath("/api/bff/catalog", { page, size: 20 }), { signal }).then(requirePage), [request, page]);
    const catalog = useResource(load, !!account);
    useEffect(() => {
        if (!showForm) return;
        formRef.current?.scrollIntoView?.({ behavior: "smooth", block: "start" });
        formRef.current?.querySelector("input")?.focus({ preventScroll: true });
    }, [showForm, editing]);

    const change = (event) => setForm(current => ({ ...current, [event.target.name]:
        event.target.type === "checkbox" ? event.target.checked : event.target.value }));
    const cancel = () => { setEditing(null); setForm(emptyForm); setShowForm(false); };
    const edit = (unit) => {
        setEditing(unit.id);
        setShowForm(true);
        setForm({ ...unit, capacity: String(unit.capacity), price: String(unit.price), description: unit.description || "" });
        setError(""); setMessage("");
    };
    const perform = async (operation, success) => {
        if (busy) return;
        setBusy(true); setError(""); setMessage("");
        try { await operation(); setMessage(success); catalog.refresh(); return true; }
        catch (failure) { setError(failure.message); return false; }
        finally { setBusy(false); }
    };
    const save = async (event) => {
        event.preventDefault();
        if (!form.name.trim() || !form.type.trim() || !Number.isInteger(Number(form.capacity))
                || Number(form.capacity) < 1 || Number(form.price) <= 0) {
            setError("Indica nombre, tipo, capacidad positiva y una tarifa positiva."); return;
        }
        const body = { name: form.name.trim(), type: form.type.trim(), capacity: Number(form.capacity),
            price: form.price, available: form.available, description: form.description };
        if (await perform(() => request(editing ? `/api/bff/catalog/${editing}` : "/api/bff/catalog",
                { method: editing ? "PUT" : "POST", body }), "Unidad guardada correctamente.")) cancel();
    };
    const remove = async (unit) => {
        if (!window.confirm(`¿Eliminar la unidad ${unit.name}? Las unidades con reservas conservan su historial y no pueden eliminarse.`)) return;
        if (await perform(() => request(`/api/bff/catalog/${unit.id}`, { method: "DELETE" }), "Unidad eliminada.")) {
            if (catalog.data?.content.length === 1 && page > 0) setPage(page - 1);
            if (editing === unit.id) cancel();
        }
    };
    return <><Navbar /><main className="app-main" id="main-content">
        <PageHeader eyebrow="ALOJAMIENTOS CON ENCANTO" title="Catálogo" description="Encuentra un lugar para descansar y sentirte en casa.">
            {catalog.data && <span className="subtle-tag">{catalog.data.totalElements} alojamientos en catálogo</span>}
            {staff && <button type="button" className="button-primary" onClick={() => { cancel(); setShowForm(true); }}><Icon name="plus" size={17} />Nueva unidad</button>}
        </PageHeader>
        {error || catalog.error ? <p role="alert">{error || catalog.error}</p> : null}
        {message && <p role="status" className="notice">{message}</p>}
        {staff && showForm && <form ref={formRef} onSubmit={save} id="unit-form">
            <h2>{editing ? `Editar unidad ${editing}` : "Crear unidad"}</h2>
            <p className="form-intro">Los detalles que harán especial la próxima estadía.</p>
            <fieldset disabled={busy}><div className="form-grid">
                <label>Nombre<input name="name" value={form.name} onChange={change} maxLength={255} placeholder="Nombre del alojamiento" required /></label>
                <label>Tipo<input name="type" value={form.type} onChange={change} maxLength={255} placeholder="Habitación, cabaña…" required /></label>
                <label>Capacidad<input name="capacity" type="number" min="1" step="1" value={form.capacity} onChange={change} required /></label>
                <label>Tarifa por noche<input name="price" type="number" min="0.01" step="0.01" value={form.price} onChange={change} required /></label>
                <label className="wide-field">Descripción<textarea name="description" value={form.description} onChange={change} maxLength={255} placeholder="Cuéntanos qué ofrece este alojamiento." /></label>
                <label className="checkbox-label wide-field"><span><input name="available" type="checkbox" checked={form.available} onChange={change} /> Habilitada para nuevas reservas</span></label>
            </div><div className="actions">
                <button type="submit"><Icon name={editing ? "check" : "plus"} size={17} />{editing ? "Guardar cambios" : "Crear unidad"}</button>
                <button type="button" onClick={cancel}>{editing ? "Cancelar edición" : "Cancelar"}</button>
            </div></fieldset>
        </form>}
        <div className="section-heading catalog-heading"><h2>Nuestros alojamientos</h2><span className="muted">Tarifas en pesos chilenos</span></div>
        {catalog.loading && <p role="status" className="loading-state">Cargando unidades…</p>}
        <div className="catalog-grid">
            {catalog.data?.content.map(unit => <article className="panel unit-card" key={unit.id}>
                <div className="unit-visual" aria-hidden="true"><span className="unit-type">{unit.type}</span><Icon name="cabin" size={51} /></div>
                <div className="unit-body"><div className="unit-title"><h3>{unit.name}</h3><span className="table-id">#{unit.id}</span></div>
                    <p className="unit-description">{unit.description || "Consulta los detalles del alojamiento con recepción."}</p>
                    <span className="unit-capacity"><Icon name="people" size={15} />{unit.type} · {unit.capacity} {Number(unit.capacity) === 1 ? "huésped" : "huéspedes"}</span>
                    <div className="unit-bottom"><div className="unit-price"><strong>{money(unit.price)}</strong><small>por noche</small></div>
                        <StatusBadge tone={unit.available ? "green" : "neutral"}>{unit.available ? "Habilitada" : "Deshabilitada"}</StatusBadge></div>
                    {staff && <div className="unit-actions">
                        <button type="button" disabled={busy} onClick={() => edit(unit)}>Editar</button>
                        <button type="button" disabled={busy} onClick={() => perform(() => request(`/api/bff/catalog/${unit.id}`,
                            { method: "PUT", body: { ...unit, available: !unit.available } }),
                            unit.available ? "Unidad deshabilitada." : "Unidad habilitada.")}>{unit.available ? "Deshabilitar" : "Habilitar"}</button>
                        <button type="button" className="button-danger" disabled={busy} onClick={() => remove(unit)}>Eliminar</button>
                    </div>}
                </div>
            </article>)}
        </div>
        {catalog.data?.content.length === 0 && <div className="panel"><EmptyState title="No hay unidades registradas" description={staff ? "Agrega el primer alojamiento usando el formulario." : "Vuelve pronto para descubrir nuestros alojamientos."} icon="cabin" /></div>}
        <Pagination data={catalog.data} page={page} onChange={setPage} loading={catalog.loading || busy} label="unidades" />
        <footer className="page-footer">AndesStay <span>Tu próximo respiro, a un paso.</span></footer>
    </main></>;
}
