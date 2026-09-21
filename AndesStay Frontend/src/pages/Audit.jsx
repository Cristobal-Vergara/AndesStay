import React, { useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";
import { loginRequest } from "../authConfig";
import { API_BASE_URL } from "../api";

function Audit() {

    const { instance, accounts } = useMsal();

    const [eventos, setEventos] = useState([]);
    const [action, setAction] = useState("");
    const [inicio, setInicio] = useState("");
    const [fin, setFin] = useState("");
    const [error, setError] = useState("");

    const obtenerToken = async () => {

        const tokenResponse =
            await instance.acquireTokenSilent({
                ...loginRequest,
                account: accounts[0]
            });

        return tokenResponse.accessToken;
    };

    const cargarEventos = async () => {

        try {

            setError("");

            const token = await obtenerToken();

            const params = new URLSearchParams();

            if (action) {
                params.append("action", action);
            }

            if (inicio) {
                params.append("inicio", inicio);
            }

            if (fin) {
                params.append("fin", fin);
            }

            const url =
                `${API_BASE_URL}/api/bff/audit`
                + (params.toString()
                    ? `?${params.toString()}`
                    : "");

            const response = await fetch(url, {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            });

            if (!response.ok) {
                throw new Error(
                    `Error al obtener auditoría: ${response.status}`
                );
            }

            const data = await response.json();

            setEventos(data);

        } catch (err) {

            console.error(err);
            setError("No se pudieron cargar los eventos de auditoría.");
        }
    };

    useEffect(() => {

        if (accounts.length > 0) {
            cargarEventos();
        }

    }, [accounts]);

    const limpiarFiltros = () => {

        setAction("");
        setInicio("");
        setFin("");

        setTimeout(() => {
            cargarEventos();
        }, 0);
    };

    return (
        <div style={{ padding: "20px" }}>

            <h1>Auditoría</h1>

            <p>
                Registro de eventos y trazabilidad de las reservas.
            </p>

            <hr />

            <h2>Filtros</h2>

            <div
                style={{
                    display: "flex",
                    gap: "15px",
                    flexWrap: "wrap",
                    alignItems: "end",
                    marginBottom: "25px"
                }}
            >

                <div>
                    <label>
                        <strong>Tipo de evento</strong>
                    </label>

                    <br />

                    <select
                        value={action}
                        onChange={(e) =>
                            setAction(e.target.value)
                        }
                    >
                        <option value="">
                            Todos
                        </option>

                        <option value="RESERVA_CREADA">
                            Reserva creada
                        </option>

                        <option value="RESERVA_CONFIRMADA">
                            Reserva confirmada
                        </option>

                        <option value="RESERVA_CANCELADA">
                            Reserva cancelada
                        </option>

                        <option value="CHECKIN_PENDIENTE">
                            Check-in pendiente
                        </option>

                        <option value="CHECKIN">
                            Check-in
                        </option>

                        <option value="CHECKOUT">
                            Check-out
                        </option>
                    </select>
                </div>

                <div>
                    <label>
                        <strong>Desde</strong>
                    </label>

                    <br />

                    <input
                        type="datetime-local"
                        value={inicio}
                        onChange={(e) =>
                            setInicio(e.target.value)
                        }
                    />
                </div>

                <div>
                    <label>
                        <strong>Hasta</strong>
                    </label>

                    <br />

                    <input
                        type="datetime-local"
                        value={fin}
                        onChange={(e) =>
                            setFin(e.target.value)
                        }
                    />
                </div>

                <button onClick={cargarEventos}>
                    Filtrar
                </button>

                <button onClick={limpiarFiltros}>
                    Limpiar filtros
                </button>

            </div>

            {error && (
                <p>{error}</p>
            )}

            <h2>
                Eventos encontrados: {eventos.length}
            </h2>

            <table
                border="1"
                cellPadding="10"
                style={{
                    borderCollapse: "collapse",
                    width: "100%"
                }}
            >

                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Acción</th>
                        <th>Reserva</th>
                        <th>Usuario</th>
                        <th>Descripción</th>
                        <th>Fecha</th>
                    </tr>
                </thead>

                <tbody>

                    {eventos.length === 0 ? (

                        <tr>
                            <td
                                colSpan="6"
                                style={{
                                    textAlign: "center"
                                }}
                            >
                                No se encontraron eventos.
                            </td>
                        </tr>

                    ) : (

                        eventos.map((evento) => (

                            <tr key={evento.id}>

                                <td>{evento.id}</td>

                                <td>{evento.action}</td>

                                <td>{evento.reservationId}</td>

                                <td>{evento.user}</td>

                                <td>{evento.description}</td>

                                <td>{evento.eventDate}</td>

                            </tr>

                        ))

                    )}

                </tbody>

            </table>

        </div>
    );
}

export default Audit;


