import { useState } from "react";
import { useMsal, useIsAuthenticated } from "@azure/msal-react";
import { Link, Navigate } from "react-router-dom";
import { loginRequest } from "../authConfig";
import AuthLayout from "../components/AuthLayout";
import MicrosoftButton from "../components/MicrosoftButton";
import Icon from "../components/Icon";

export default function Login() {
    const { instance } = useMsal();
    const authenticated = useIsAuthenticated();
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    if (authenticated) return <Navigate to="/dashboard" replace />;
    const login = async () => {
        if (busy) return;
        setBusy(true); setError("");
        try { await instance.loginRedirect(loginRequest); }
        catch { setError("No se pudo iniciar sesión con Microsoft. Vuelve a intentarlo."); setBusy(false); }
    };
    return <AuthLayout>
        <span className="welcome-icon"><Icon name="cabin" size={28} /></span>
        <p className="eyebrow">TU ESTADÍA, EN UN SOLO LUGAR</p>
        <h2>Qué bueno verte.</h2>
        <p className="login-intro">Inicia sesión para descubrir nuestros alojamientos y gestionar tus reservas.</p>
        {error && <p role="alert">{error}</p>}
        <MicrosoftButton busy={busy} onClick={login}>Iniciar sesión con mi correo</MicrosoftButton>
        <div className="login-security"><Icon name="shield" size={16} /><span>Autenticación protegida por Microsoft</span></div>
        <div className="login-divider" />
        <p className="login-help">¿Es tu primera visita?<br /><Link to="/register" className="register-link">Crear mi perfil en AndesStay <Icon name="arrow" size={15} /></Link></p>
        <p className="login-account-note">Usa tu cuenta Microsoft o verifica otro correo con un código. Si tu correo ya está vinculado a Microsoft, entra con esa cuenta.</p>
    </AuthLayout>;
}
