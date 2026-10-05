import Icon from "./Icon";
export default function Pagination({ data, page, onChange, loading = false, label = "resultados" }) {
    if (!data) return null;
    return <nav aria-label={`Paginación de ${label}`} className="pagination">
        <span>{data.totalElements} resultados <span className="pagination-dot">·</span> Página {data.totalPages ? page + 1 : 0} de {data.totalPages}</span>
        <div className="pagination-buttons">
            <button type="button" disabled={loading || page === 0} onClick={() => onChange(page - 1)}><Icon name="chevron" size={15} className="rotate-180" />Anterior</button>
            <button type="button" disabled={loading || page + 1 >= data.totalPages} onClick={() => onChange(page + 1)}>Siguiente<Icon name="chevron" size={15} /></button>
        </div>
    </nav>;
}
