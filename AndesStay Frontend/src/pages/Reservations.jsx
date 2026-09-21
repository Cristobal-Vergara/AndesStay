import { useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";
import Navbar from "../components/Navbar";
import { loginRequest } from "../authConfig";
import { API_BASE_URL } from "../api";


function Reservations() {

    const { instance, accounts } = useMsal();

    const usuario = accounts[0];

    const roles = usuario?.idTokenClaims?.roles || [];

    const puedeCambiarEstado =
        roles.includes("Admin") ||
        roles.includes("Recepcionista");

    const [reservations, setReservations] = useState([]);

    const [guestName, setGuestName] = useState("");
    const [unitId, setUnitId] = useState("");
    const [checkIn, setCheckIn] = useState("");
    const [checkOut, setCheckOut] = useState("");

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");

    const obtenerToken = async () => {

        const tokenResponse =
            await instance.acquireTokenSilent({
                ...loginRequest,
                account: usuario
            });

        return tokenResponse.accessToken;
    };

    const cargarReservas = async () => {

        try {

            const token = await obtenerToken();

            const response = await fetch(
                `${API_BASE_URL}/api/bff/reservations`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }
            );

            if (!response.ok) {
                throw new Error(
                    `Error al obtener las reservas: ${response.status}`
                );
            }

            const data = await response.json();

            setReservations(data);

        } catch (error) {

            console.error(error);

            setError(
                "No se pudieron cargar las reservas."
            );

        } finally {

            setLoading(false);
        }
    };

    useEffect(() => {

        if (usuario) {
            cargarReservas();
        }

    }, [instance, usuario]);

    const crearReserva = async (event) => {

        event.preventDefault();

        setMessage("");
        setError("");

        try {

            const token = await obtenerToken();

            console.log("RESERVA ENVIADA:", {
                guestName,
                unitId: Number(unitId),
                checkIn,
                checkOut
            });

            const response = await fetch(
                `${API_BASE_URL}/api/bff/reservations`,
                {
                    method: "POST",

                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${token}`
                    },

                    body: JSON.stringify({
                        guestName: guestName,
                        unitId: Number(unitId),
                        checkIn: checkIn,
                        checkOut: checkOut
                    })
                }
            );

            if (!response.ok) {

                const errorText = await response.text();

                throw new Error(
                    errorText || "No se pudo crear la reserva"
                );
            }

            setMessage(
                "Reserva creada correctamente."
            );

            setGuestName("");
            setUnitId("");
            setCheckIn("");
            setCheckOut("");

            await cargarReservas();

        } catch (error) {

            console.error(error);

            setError(
                error.message ||
                "Error al crear la reserva."
            );
        }
    };

    const cambiarEstado = async (
        reservationId,
        nuevoEstado
    ) => {

        setMessage("");
        setError("");

        try {

            const token = await obtenerToken();

            console.log("TOKEN OBTENIDO:", token ? "SI" : "NO");
            console.log("URL:", `/api/bff/reservations/${reservationId}/status`);
            console.log("ESTADO:", nuevoEstado);

            const response = await fetch(
                `${API_BASE_URL}/api/bff/reservations/${reservationId}/status`,
                {
                    method: "PUT",

                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${token}`
                    },

                    body: JSON.stringify({
                        status: nuevoEstado
                    })
                }
            );

            if (!response.ok) {

                const errorText = await response.text();

                throw new Error(
                    errorText ||
                    "No se pudo cambiar el estado"
                );
            }

            setMessage(
                `Estado de la reserva ${reservationId} actualizado correctamente.`
            );

            await cargarReservas();

        } catch (error) {

            console.error(error);

            setError(
                error.message ||
                "Error al cambiar el estado."
            );
        }
    };

    return (
        <div>

            <Navbar />

            <hr />

            <h1>Reservas</h1>

            <p>
                Usuario: {usuario?.username}
            </p>

            <p>
                Rol: {roles.length > 0
                    ? roles.join(", ")
                    : "Sin rol asignado"}
            </p>

            <p>
                Gestión de reservas de AndesStay.
            </p>

            <hr />

            <h2>Crear reserva</h2>

            <form onSubmit={crearReserva}>

                <div>

                    <label>
                        Nombre del huésped:
                    </label>

                    <br />

                    <input
                        type="text"
                        value={guestName}
                        onChange={(e) =>
                            setGuestName(e.target.value)
                        }
                        required
                    />

                </div>

                <br />

                <div>

                    <label>
                        ID de unidad:
                    </label>

                    <br />

                    <input
                        type="number"
                        value={unitId}
                        onChange={(e) =>
                            setUnitId(e.target.value)
                        }
                        required
                    />

                </div>

                <br />

                <div>

                    <label>
                        Check-in:
                    </label>

                    <br />

                    <input
                        type="date"
                        value={checkIn}
                        onChange={(e) =>
                            setCheckIn(e.target.value)
                        }
                        required
                    />

                </div>

                <br />

                <div>

                    <label>
                        Check-out:
                    </label>

                    <br />

                    <input
                        type="date"
                        value={checkOut}
                        onChange={(e) =>
                            setCheckOut(e.target.value)
                        }
                        required
                    />

                </div>

                <br />

                <button type="submit">
                    Crear reserva
                </button>

            </form>

            <br />

            {message && (
                <p>{message}</p>
            )}

            {error && (
                <p>{error}</p>
            )}

            <hr />

            <h2>Reservas existentes</h2>

            {loading && (
                <p>
                    Cargando reservas...
                </p>
            )}

            {!loading && (

                <table border="1">

                    <thead>

                        <tr>

                            <th>ID</th>
                            <th>Huésped</th>
                            <th>Unidad</th>
                            <th>Check-in</th>
                            <th>Check-out</th>
                            <th>Estado</th>

                            {puedeCambiarEstado && (
                                <th>
                                    Cambiar estado
                                </th>
                            )}

                        </tr>

                    </thead>

                    <tbody>

                        {reservations.map(
                            (reservation) => (

                                <tr
                                    key={reservation.id}
                                >

                                    <td>
                                        {reservation.id}
                                    </td>

                                    <td>
                                        {reservation.guestName}
                                    </td>

                                    <td>
                                        {reservation.unitId}
                                    </td>

                                    <td>
                                        {reservation.checkIn}
                                    </td>

                                    <td>
                                        {reservation.checkOut}
                                    </td>

                                    <td>
                                        {reservation.status}
                                    </td>

                                    {puedeCambiarEstado && (

                                        <td>

                                            <select
                                                value={
                                                    reservation.status
                                                }
                                                onChange={(e) =>
                                                    cambiarEstado(
                                                        reservation.id,
                                                        e.target.value
                                                    )
                                                }
                                            >

                                                <option value="CREADA">
                                                    CREADA
                                                </option>

                                                <option value="CONFIRMADA">
                                                    CONFIRMADA
                                                </option>

                                                <option value="CANCELADA">
                                                    CANCELADA
                                                </option>

                                                <option value="CHECKIN_PENDIENTE">
                                                    CHECKIN_PENDIENTE
                                                </option>

                                                <option value="EN_ESTADÍA">
                                                    EN_ESTADÍA
                                                </option>

                                                <option value="CHECKOUT">
                                                    CHECKOUT
                                                </option>

                                            </select>

                                        </td>

                                    )}

                                </tr>

                            )
                        )}

                    </tbody>

                </table>

            )}

        </div>
    );
}

export default Reservations;
