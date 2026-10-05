import { useEffect, useRef, useState } from "react";
import { useMsal } from "@azure/msal-react";
import { NavLink, useLocation } from "react-router-dom";
import Brand from "./Brand";
import Icon from "./Icon";
import { useApi } from "../hooks/useApi";
import { useAccount } from "../auth/AccountContext";

const roleLabels = { Admin: "Administración", Recepcionista: "Recepción", Huesped: "Huésped", Auditor: "Auditoría" };
export default function Navbar() {
    const { instance } = useMsal();
    const { account, roles } = useApi();
    const session = useAccount();
    const staff = roles.includes("Admin") || roles.includes("Recepcionista");
    const guest = roles.includes("Huesped");
    const [open, setOpen] = useState(false);
    const menuButton = useRef(null);
    const sidebar = useRef(null);
    const location = useLocation();
    const links = [
        { to: "/dashboard", label: "Inicio", icon: "home", visible: true },
        { to: "/reservations", label: "Reservas", icon: "calendar", visible: staff || guest },
        { to: "/catalog", label: "Catálogo", icon: "cabin", visible: staff || guest },
        { to: "/reports", label: "Reportes", icon: "chart", visible: roles.includes("Admin") },
        { to: "/audit", label: "Auditoría", icon: "shield", visible: roles.includes("Admin") || roles.includes("Auditor") },
    ].filter(link => link.visible);
    const current = links.find(link => link.to === location.pathname)?.label || "Inicio";
    const name = session?.profile?.displayName || account?.name || account?.username || "Mi cuenta";
    const role = roles.map(value => roleLabels[value] || value).join(" · ") || "Sin rol asignado";
    useEffect(() => {
        if (!open) return;
        const previous = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        const controls = [...sidebar.current.querySelectorAll("a, button")];
        const focusTimer = setTimeout(() => controls[0]?.focus(), 80);
        const escape = event => {
            if (event.key === "Escape") { setOpen(false); menuButton.current?.focus(); }
            if (event.key === "Tab") {
                const first = controls[0];
                const last = controls.at(-1);
                if (!controls.includes(document.activeElement)) { event.preventDefault(); (event.shiftKey ? last : first)?.focus(); }
                else if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
                else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
            }
        };
        document.addEventListener("keydown", escape);
        return () => { clearTimeout(focusTimer); document.body.style.overflow = previous; document.removeEventListener("keydown", escape); };
    }, [open]);
    return <>
        <a className="skip-link" href="#main-content">Ir al contenido</a>
        {open && <button className="nav-backdrop" tabIndex={-1} aria-label="Cerrar navegación" onClick={() => { setOpen(false); menuButton.current?.focus(); }} />}
        <aside ref={sidebar} className={`sidebar ${open ? "sidebar-open" : ""}`} id="main-navigation">
            <NavLink className="brand-link" to="/dashboard" onClick={() => setOpen(false)} aria-label="AndesStay, inicio"><Brand /></NavLink>
            <p className="nav-caption">TU ESPACIO</p>
            <nav aria-label="Navegación principal" className="nav-links">
                {links.map(link => <NavLink key={link.to} to={link.to} onClick={() => setOpen(false)}
                    className={({ isActive }) => `nav-link ${isActive ? "nav-link-active" : ""}`}>
                    <Icon name={link.icon} /><span>{link.label}</span><Icon name="chevron" size={15} className="nav-chevron" />
                </NavLink>)}
            </nav>
            <div className="sidebar-bottom">
                <div className="sidebar-note"><Icon name="leaf" size={22} /><strong>Un lugar para conectar.</strong><p>Tu próxima estadía empieza aquí.</p></div>
                <button className="logout-button" type="button" onClick={() => instance.logoutRedirect()}><Icon name="logout" />Cerrar sesión</button>
                <small className="sidebar-footer">AndesStay · Hecho para hospedar</small>
            </div>
        </aside>
        <header className="topbar">
            <div className="topbar-left"><button type="button" ref={menuButton} className="icon-button mobile-menu"
                aria-label={open ? "Cerrar menú" : "Abrir menú"} aria-expanded={open} aria-controls="main-navigation" onClick={() => setOpen(!open)}>
                <Icon name={open ? "close" : "menu"} /></button><span className="breadcrumb">Mi espacio <span aria-hidden="true">/</span> <strong>{current}</strong></span></div>
            <NavLink to="/register" className="account-chip" aria-label="Mi perfil en AndesStay"><span className="avatar">{name.charAt(0).toUpperCase()}</span>
                <span className="account-details"><strong>{name}</strong><small>{role}</small></span></NavLink>
        </header>
    </>;
}
