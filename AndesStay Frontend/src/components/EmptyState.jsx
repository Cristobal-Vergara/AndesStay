import Icon from "./Icon";
export default function EmptyState({ title, description, icon = "calendar" }) {
    return <div className="empty-state"><span className="empty-icon"><Icon name={icon} size={25} /></span>
        <h3>{title}</h3>{description && <p>{description}</p>}
    </div>;
}
