import { useEffect, useState } from "react";
import { useMsal } from "@azure/msal-react";
import Navbar from "../components/Navbar";
import { loginRequest } from "../authConfig";
import { API_BASE_URL } from "../api";

function Catalog() {

    const { instance, accounts } = useMsal();

    const usuario = accounts[0];

    const roles = usuario?.idTokenClaims?.roles || [];

    const [units, setUnits] = useState([]);

    const [loading, setLoading] = useState(true);

    const [error, setError] = useState("");

    useEffect(() => {

        const cargarUnidades = async () => {

            try {

                const tokenResponse =
                    await instance.acquireTokenSilent({
                        ...loginRequest,
                        account: usuario
                    });
                    


                const response = await fetch(
                    `${API_BASE_URL}/api/bff/catalog`,
                    {
                        headers: {
                            Authorization:
                                `Bearer ${tokenResponse.accessToken}`
                        }
                    }
                );

                if (!response.ok) {
                    const errorText = await response.text();
                    console.error("RESPUESTA API:", errorText);
                    throw new Error(`Error al obtener las unidades: ${response.status}`);
                }

                const data = await response.json();

                setUnits(data);

            } catch (error) {

                console.error(error);

                setError(
                    "No se pudieron cargar las unidades."
                );

            } finally {

                setLoading(false);
            }
        };

        if (usuario) {
            cargarUnidades();
        }

    }, [instance, usuario]);

    return (
        <div>

            <Navbar />

            <hr />

            <h1>Catálogo</h1>

            <p>
                Usuario: {usuario?.username}
            </p>

            <p>
                Rol: {roles.length > 0
                    ? roles.join(", ")
                    : "Sin rol asignado"}
            </p>

            <p>
                Gestión de habitaciones, cabañas,
                tarifas y disponibilidad.
            </p>

            <hr />

            <h2>Unidades disponibles en el catálogo</h2>

            {loading && (
                <p>
                    Cargando unidades...
                </p>
            )}

            {error && (
                <p>
                    {error}
                </p>
            )}

            {!loading && !error && (

                <table border="1">

                    <thead>

                        <tr>
                            <th>ID</th>
                            <th>Nombre</th>
                            <th>Tipo</th>
                            <th>Capacidad</th>
                            <th>Tarifa</th>
                            <th>Disponible</th>
                            <th>Descripción</th>
                        </tr>

                    </thead>

                    <tbody>

                        {units.length === 0 ? (

                            <tr>

                                <td colSpan="7">
                                    No existen unidades registradas.
                                </td>

                            </tr>

                        ) : (

                            units.map((unit) => (

                                <tr key={unit.id}>

                                    <td>
                                        {unit.id}
                                    </td>

                                    <td>
                                        {unit.name}
                                    </td>

                                    <td>
                                        {unit.type}
                                    </td>

                                    <td>
                                        {unit.capacity}
                                    </td>

                                    <td>
                                        ${unit.price}
                                    </td>

                                    <td>
                                        {unit.available
                                            ? "Sí"
                                            : "No"}
                                    </td>

                                    <td>
                                        {unit.description}
                                    </td>

                                </tr>

                            ))

                        )}

                    </tbody>

                </table>

            )}

        </div>
    );
}

export default Catalog;
