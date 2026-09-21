import { useMsal } from "@azure/msal-react";
import { Link } from "react-router-dom";

function Navbar() {

    const { instance, accounts } = useMsal();

    const usuario = accounts[0];

    const roles = usuario?.idTokenClaims?.roles || [];

    const esAdmin = roles.includes("Admin");
    const esRecepcionista = roles.includes("Recepcionista");
    const esHuesped = roles.includes("Huesped");
    const esAuditor = roles.includes("Auditor");

    const handleLogout = () => {
        instance.logoutRedirect();
    };

    return (
        <nav>

            <strong>AndesStay</strong>

            {" | "}

            <Link to="/dashboard">
                Dashboard
            </Link>

            {" | "}

            {(esAdmin || esRecepcionista || esHuesped) && (
                <Link to="/reservations">
                    Reservas
                </Link>
            )}

            {" | "}

            {(esAdmin || esRecepcionista) && (
                <Link to="/catalog">
                    Catálogo
                </Link>
            )}

            {" | "}

            {esAdmin && (
                <Link to="/reports">
                    Reportería
                </Link>
            )}

            {" | "}

            {(esAdmin || esAuditor) && (
                <Link to="/audit">
                    Auditoría
                </Link>
            )}

            {" | "}

            <button onClick={handleLogout}>
                Cerrar sesión
            </button>

        </nav>
    );
}

export default Navbar;
