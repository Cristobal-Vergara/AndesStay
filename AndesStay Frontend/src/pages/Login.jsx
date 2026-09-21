import { useMsal, useIsAuthenticated } from "@azure/msal-react";
import { Navigate } from "react-router-dom";
import { loginRequest } from "../authConfig";

function Login() {
    const { instance } = useMsal();
    const isAuthenticated = useIsAuthenticated();

    if (isAuthenticated) {
        return <Navigate to="/dashboard" replace />;
    }

    const handleLogin = () => {
        instance.loginRedirect(loginRequest);
    };

    return (
        <div>
            <h1>AndesStay</h1>
            <h2>Iniciar sesión</h2>

            <button onClick={handleLogin}>
                Iniciar sesión con Microsoft
            </button>
        </div>
    );
}

export default Login;
