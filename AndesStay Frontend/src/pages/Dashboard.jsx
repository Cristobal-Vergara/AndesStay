import { useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";
import Navbar from "../components/Navbar";
import { loginRequest } from "../authConfig";
import { API_BASE_URL } from "../api";

function Dashboard() {

    const { instance, accounts } = useMsal();

    const usuario = accounts[0];

    const roles = usuario?.idTokenClaims?.roles || [];

    const esAdmin = roles.includes("Admin");
    const esRecepcionista =
        roles.includes("Recepcionista");
    const esHuesped = roles.includes("Huesped");

    const [kpis, setKpis] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {

        const cargarKPIs = async () => {

            try {

                setLoading(true);
                setError("");

                const tokenResponse =
                    await instance.acquireTokenSilent({
                        ...loginRequest,
                        account: usuario
                    });

                const response = await fetch(
                    `${API_BASE_URL}/api/bff/reports`,
                    {
                        headers: {
                            Authorization:
                                `Bearer ${tokenResponse.accessToken}`
                        }
                    }
                );

                if (!response.ok) {
                    throw new Error(
                        `Error al obtener KPIs: ${response.status}`
                    );
                }

                const data = await response.json();

                setKpis(data);

            } catch (error) {

                console.error(error);

                setError(
                    "No se pudieron cargar los indicadores."
                );

            } finally {

                setLoading(false);
            }
        };

        if (usuario && esAdmin) {
            cargarKPIs();
        }

    }, [instance, usuario, esAdmin]);

    return (
        <div>

            <Navbar />

            <hr />

            <h1>Dashboard</h1>

            <h2>Bienvenido a AndesStay</h2>

            <p>
                Usuario: {usuario?.username}
            </p>

            <p>
                Rol: {roles.length > 0
                    ? roles.join(", ")
                    : "Sin rol asignado"}
            </p>

            <hr />

            {esAdmin && (

                <div>

                    <h2>Resumen de ocupación</h2>

                    {loading && (
                        <p>
                            Cargando indicadores...
                        </p>
                    )}

                    {error && (
                        <p>
                            {error}
                        </p>
                    )}

                    {!loading && !error && kpis && (

                        <div>

                            <h3>KPIs</h3>

                            <p>
                                <strong>
                                    Total de reservas:
                                </strong>{" "}
                                {kpis.totalReservas}
                            </p>

                            <p>
                                <strong>
                                    Reservas creadas:
                                </strong>{" "}
                                {kpis.creadas}
                            </p>

                            <p>
                                <strong>
                                    Reservas confirmadas:
                                </strong>{" "}
                                {kpis.confirmadas}
                            </p>

                            <p>
                                <strong>
                                    Reservas canceladas:
                                </strong>{" "}
                                {kpis.canceladas}
                            </p>

                            <p>
                                <strong>
                                    Check-in pendiente:
                                </strong>{" "}
                                {kpis.checkinPendiente}
                            </p>

                            <p>
                                <strong>
                                    Huéspedes en estadía:
                                </strong>{" "}
                                {kpis.enEstadia}
                            </p>

                            <p>
                                <strong>
                                    Reservas con checkout:
                                </strong>{" "}
                                {kpis.checkout}
                            </p>

                            <p>
                                <strong>
                                    Ocupación activa:
                                </strong>{" "}
                                {kpis.ocupacionActiva}
                            </p>

                        </div>

                    )}

                </div>

            )}

            {esRecepcionista && (

                <div>

                    <h2>Operaciones del día</h2>

                    <h3>Llegadas</h3>

                    <p>
                        Llegadas del día próximamente.
                    </p>

                    <h3>Salidas</h3>

                    <p>
                        Salidas del día próximamente.
                    </p>

                </div>

            )}

            {esHuesped && (

                <div>

                    <h2>Mis reservas</h2>

                    <p>
                        Aquí aparecerán tus reservas y
                        sus estados.
                    </p>

                </div>

            )}

        </div>
    );
}

export default Dashboard;
