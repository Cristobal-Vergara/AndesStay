import React, { useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";
import { loginRequest } from "../authConfig";
import { API_BASE_URL } from "../api";

function Reports() {

    const { instance, accounts } = useMsal();

    const [kpis, setKpis] = useState(null);
    const [error, setError] = useState("");

    useEffect(() => {

        const cargarReportes = async () => {

            try {

                const usuario = accounts[0];

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
                        `Error al obtener reportes: ${response.status}`
                    );
                }

                const data = await response.json();

                setKpis(data);

            } catch (err) {

                console.error(err);
                setError("No se pudieron cargar los reportes.");
            }
        };

        if (accounts.length > 0) {
            cargarReportes();
        }

    }, [instance, accounts]);

    if (error) {
        return (
            <div>
                <h2>Reportería</h2>
                <p>{error}</p>
            </div>
        );
    }

    if (!kpis) {
        return (
            <div>
                <h2>Reportería</h2>
                <p>Cargando reportes...</p>
            </div>
        );
    }

    const reservasPorHora =
        Object.entries(kpis.reservasPorHora || {})
            .sort((a, b) => Number(a[0]) - Number(b[0]));

    const reservasPorUnidad =
        Object.entries(kpis.reservasPorUnidad || {})
            .sort((a, b) => b[1] - a[1]);

    return (
        <div style={{ padding: "20px" }}>

            <h1>Reportería</h1>

            <hr />

            <h2>KPIs generales</h2>

            <div
                style={{
                    display: "grid",
                    gridTemplateColumns:
                        "repeat(auto-fit, minmax(180px, 1fr))",
                    gap: "15px",
                    marginBottom: "30px"
                }}
            >

                <div>
                    <h3>Total reservas</h3>
                    <p>{kpis.totalReservas}</p>
                </div>

                <div>
                    <h3>Creadas</h3>
                    <p>{kpis.creadas}</p>
                </div>

                <div>
                    <h3>Confirmadas</h3>
                    <p>{kpis.confirmadas}</p>
                </div>

                <div>
                    <h3>Canceladas</h3>
                    <p>{kpis.canceladas}</p>
                </div>

                <div>
                    <h3>Check-in pendiente</h3>
                    <p>{kpis.checkinPendiente}</p>
                </div>

                <div>
                    <h3>En estadía</h3>
                    <p>{kpis.enEstadia}</p>
                </div>

                <div>
                    <h3>Check-out</h3>
                    <p>{kpis.checkout}</p>
                </div>

                <div>
                    <h3>Ocupación activa</h3>
                    <p>{kpis.ocupacionActiva}</p>
                </div>

            </div>

            <hr />

            <h2>Tiempo de ciclo</h2>

            <div>
                <h3>Promedio de creación a confirmación</h3>

                <p>
                    {kpis.tiempoCicloPromedioMinutos} minutos
                </p>
            </div>

            <hr />

            <h2>Reservas por hora</h2>

            {reservasPorHora.length === 0 ? (

                <p>No existen datos de reservas por hora.</p>

            ) : (

                <table
                    border="1"
                    cellPadding="10"
                    style={{
                        borderCollapse: "collapse",
                        marginBottom: "30px"
                    }}
                >
                    <thead>
                        <tr>
                            <th>Hora</th>
                            <th>Reservas</th>
                        </tr>
                    </thead>

                    <tbody>

                        {reservasPorHora.map(
                            ([hora, cantidad]) => (

                                <tr key={hora}>
                                    <td>{hora}:00</td>
                                    <td>{cantidad}</td>
                                </tr>

                            )
                        )}

                    </tbody>
                </table>

            )}

            <hr />

            <h2>Unidades más demandadas</h2>

            {reservasPorUnidad.length === 0 ? (

                <p>No existen datos de unidades.</p>

            ) : (

                <table
                    border="1"
                    cellPadding="10"
                    style={{
                        borderCollapse: "collapse"
                    }}
                >
                    <thead>
                        <tr>
                            <th>Unidad</th>
                            <th>Total de reservas</th>
                        </tr>
                    </thead>

                    <tbody>

                        {reservasPorUnidad.map(
                            ([unitId, cantidad]) => (

                                <tr key={unitId}>
                                    <td>Unidad {unitId}</td>
                                    <td>{cantidad}</td>
                                </tr>

                            )
                        )}

                    </tbody>
                </table>

            )}

        </div>
    );
}

export default Reports;
