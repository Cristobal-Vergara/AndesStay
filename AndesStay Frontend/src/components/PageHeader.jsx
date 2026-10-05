export default function PageHeader({ eyebrow = "ANDESSTAY", title, description, children }) {
    return <header className="page-header">
        <div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1>{description && <p className="page-description">{description}</p>}</div>
        {children && <div className="header-actions">{children}</div>}
    </header>;
}
