import { useCallback, useState } from "react";
import { useMsal, useIsAuthenticated } from "@azure/msal-react";
import { Link } from "react-router-dom";
import { loginRequest } from "../authConfig";
import { useApi } from "../hooks/useApi";
import { useResource } from "../hooks/useResource";
import AuthLayout from "../components/AuthLayout";
import MicrosoftButton from "../components/MicrosoftButton";
import Icon from "../components/Icon";
import { useAccount } from "../auth/AccountContext";

export default function Register() {
    const { instance } = useMsal();
    const authenticated = useIsAuthenticated();
    const { request, account, roles } = useApi();
    const session = useAccount();
    const [displayName, setDisplayName] = useState(account?.name || "");
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [savedProfile, setSavedProfile] = useState(null);
    const load = useCallback(signal => request("/api/v1/bff/account/profile", { signal }), [request]);
    const fallback = useResource(load, !session && authenticated && !!account);
    const resource = session ? { data: session.profile, loading: session.loading, error: session.error, refresh: session.refreshProfile } : fallback;
    const profile = savedProfile || resource.data;
    const registered = !!profile?.registered;
    const effectiveRoles = profile?.roles || roles;
    const canEnter = effectiveRoles.some(role => ["Admin", "Recepcionista", "Huesped", "Auditor"].includes(role));

    const connect = async () => {
        if (busy) return;
        setError(""); setBusy(true);
        try {
            await instance.loginRedirect({ ...loginRequest, prompt: "create",
                redirectStartPage: new URL("/register", window.location.origin).href });
        } catch {
            setError("No se pudo conectar con Microsoft. Vuelve a intentarlo."); setBusy(false);
        }
    };
    const register = async event => {
        event.preventDefault();
        if (busy) return;
        const name = displayName.trim();
        if (name.length < 2 || name.length > 120) {
            setError("Indica un nombre de entre 2 y 120 caracteres."); return;
        }
        setError(""); setBusy(true);
        try {
            const result = await request("/api/v1/bff/account/register", { method: "POST", body: { displayName: name } });
            setSavedProfile(result); session?.completeProfile(result);
        }
        catch (failure) {
            setError(failure.message);
            if (failure.status === 409) resource.refresh();
        } finally { setBusy(false); }
    };
    return <AuthLayout registration>
        <Link to={authenticated ? "/dashboard" : "/login"} className="auth-back"><Icon name="arrow" size={15} className="rotate-180" />{authenticated ? "Volver a mi espacio" : "Volver al inicio de sesión"}</Link>
        <span className="welcome-icon"><Icon name={registered ? "check" : "people"} size={28} /></span>
        <p className="eyebrow">{registered ? "TU ESPACIO EN ANDESSTAY" : "BIENVENIDO A UNA NUEVA ESTADÍA"}</p>
        <h2>{registered ? "Tu perfil está listo." : "Sé parte de AndesStay."}</h2>
        <p className="login-intro">{registered ? "Un solo perfil para acompañar tus próximas estadías." : "Registra tu perfil con tu correo y la seguridad de Microsoft."}</p>
        {error || resource.error ? <p role="alert">{error || resource.error}</p> : null}
        {!authenticated && <>
            <ol className="registration-steps"><li><span>01</span><div><strong>Verifica tu correo</strong><small>Microsoft comprueba tu identidad.</small></div></li>
                <li><span>02</span><div><strong>Completa tu perfil</strong><small>Dinos cómo prefieres que te llamemos.</small></div></li></ol>
            <MicrosoftButton busy={busy} onClick={connect}>Registrarme con mi correo</MicrosoftButton>
            <div className="login-security"><Icon name="shield" size={16} /><span>Tu identidad se verifica en Microsoft</span></div>
            <p className="registration-access-note">Usa tu cuenta Microsoft o verifica otro correo con un código enviado a tu bandeja de entrada.</p>
        </>}
        {authenticated && resource.loading && !savedProfile && <p className="loading-state" role="status">Comprobando tu perfil…</p>}
        {authenticated && resource.error && <button type="button" onClick={resource.refresh}>Volver a intentar</button>}
        {authenticated && registered && <>
            {savedProfile && <p role="status" className="notice">Tu perfil se registró correctamente.</p>}
            {effectiveRoles.includes("Huesped") && <p className="registration-access-note">Tu acceso como huésped está listo. Ya puedes crear y consultar tus reservas.</p>}
            <div className="registered-profile"><span className="avatar">{profile.displayName.charAt(0).toUpperCase()}</span>
                <div><strong>{profile.displayName}</strong><small>{profile.email || account?.username}</small></div><Icon name="check" size={20} /></div>
            {canEnter ? <Link to="/dashboard" className="button button-primary registration-submit">Ir a mi espacio <Icon name="arrow" size={17} /></Link>
                : <p className="registration-access-note">Tu perfil está guardado, pero tu cuenta todavía no tiene permisos de AndesStay. Vuelve a iniciar sesión o contacta con soporte.</p>}
        </>}
        {authenticated && !registered && !resource.loading && !resource.error && <form className="registration-form" onSubmit={register}>
            <fieldset disabled={busy}><label>Nombre completo<input name="displayName" autoComplete="name" value={displayName}
                onChange={event => setDisplayName(event.target.value)} minLength={2} maxLength={120} placeholder="Tu nombre y apellido" required /></label>
                <label>Correo verificado<input value={profile?.email || account?.username || ""} readOnly aria-describedby="account-explanation" /></label>
                <p id="account-explanation" className="field-help">Este correo proviene de tu identidad verificada por Microsoft y quedará vinculado a tu perfil.</p>
                <button type="submit" className="registration-submit"><Icon name="check" size={17} />{busy ? "Registrando tu perfil…" : "Completar registro"}</button>
            </fieldset>
        </form>}
        <div className="login-divider" /><p className="login-help"><Icon name="shield" size={14} /> Tu identidad está protegida por Microsoft.</p>
    </AuthLayout>;
}
