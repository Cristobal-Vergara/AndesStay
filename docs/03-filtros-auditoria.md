# Paso 3: filtros de auditoría de la pantalla a la base de datos

Actualización: la lista de auditoría ahora es paginada y el reloj de la aplicación usa una zona configurable. El contrato actual y las reglas de fechas se explican en [el paso 6](06-catalogo-reportes-interfaz.md).

La pantalla enviaba action, inicio y fin, pero el BFF llamaba siempre a /api/audit
sin parámetros. El backend también necesitaba ambas fechas para aplicar un límite:
elegir solo Desde o Hasta devolvía eventos fuera del período solicitado.

## Qué cambió y por qué

### BFF

- Recibe user, action, inicio y fin y reenvía los valores no vacíos al backend.
  Los parámetros ajenos a esa lista no se reenvían.
- Construye la URI mediante variables de plantilla. Esto codifica los valores sin
  convertir un signo + en un espacio ni un & en otro parámetro.
- Conserva el bearer token de la solicitud y devuelve application/json para la lista.
- Utiliza el RestClient.Builder proporcionado por Spring. Se clona antes de fijar
  la URL para evitar modificar el constructor compartido. Esto permite comprobar
  las solicitudes HTTP del servicio real con un servidor de pruebas.
- Conserva el manejo de errores del paso 2: los 4xx mantienen código y explicación;
  los fallos internos o de conexión se convierten en un 502 con mensaje general.

### Backend

- Una consulta con parámetros aplica todos los filtros presentes mediante AND.
  Sustituye las ramas para combinaciones concretas, que omitían fechas individuales.
- Usuario y acción se comparan por igualdad, sin buscar fragmentos. Los espacios
  exteriores se eliminan y un campo vacío equivale a no aplicar ese filtro.
- inicio incluye eventos cuya fecha sea mayor o igual al límite; fin incluye los
  de fecha menor o igual. Ambos funcionan independientemente y juntos.
- Las fechas usan fecha y hora local ISO, por ejemplo 2026-10-02T14:30. No se
  interpreta un día sin hora ni se convierte entre zonas horarias en este paso.
  Hasta representa ese instante concreto, sin ampliar automáticamente el minuto.
- Un formato inválido o un inicio posterior a fin devuelve 400 con un ProblemDetail
  que explica el problema. No se consulta la base con un rango invertido.
- Los eventos se ordenan por fecha descendente y luego ID descendente. El segundo
  criterio hace estable el orden cuando varios eventos tienen la misma fecha.
- Se mantienen las consultas que utiliza ReportService para sus cálculos.
  La lectura de auditoría sigue autorizada únicamente para Admin y Auditor.

### Pantalla de auditoría

- Añade el filtro de usuario y las acciones Reserva actualizada y Reserva eliminada,
  que ya registra el servicio de reservas.
- Filtrar envía los valores del formulario; Limpiar filtros vacía los campos y
  solicita explícitamente la lista sin parámetros.
- El setTimeout anterior no solucionaba el problema: su función conservaba los
  valores del render anterior. Ahora la solicitud recibe los filtros como argumento.
- Muestra el campo detail de los errores de la API y evita mantener resultados
  antiguos cuando la búsqueda falla.
- Indica la carga y deshabilita los botones mientras se consulta. Un número de
  solicitud impide que una respuesta antigua sustituya una búsqueda más reciente.
  La limpieza del efecto invalida respuestas pendientes al desmontar o cambiar cuenta.
- La carga inicial actualiza el estado desde las respuestas asíncronas. La lectura
  remota se separó de la actualización de la pantalla para respetar las reglas de React.

## Ejemplos

| Filtros | Resultado |
| --- | --- |
| Sin filtros | Todos los eventos, del más reciente al más antiguo |
| action=RESERVA_CONFIRMADA | Solo eventos de confirmación |
| inicio=2026-10-02T10:00 | Eventos desde ese instante, incluido |
| fin=2026-10-03T10:00 | Eventos hasta ese instante, incluido |
| user y action y ambas fechas | Eventos que cumplen los cuatro criterios |
| inicio posterior a fin | 400 con explicación |
| Limpiar filtros | Nueva consulta sin los valores anteriores |

## Verificación

137 pruebas aprobadas, sin fallos ni errores: 105 del backend y 32 del BFF.
Incluyen las 94 pruebas de reservas de los pasos anteriores y 43 nuevas de auditoría.

- 20 casos con JPA y H2: las 16 combinaciones de filtros, orden, límites inclusivos,
  espacios, rango invertido y valores sin coincidencias.
- 11 casos HTTP del backend: permisos, autenticación, propagación de filtros,
  formatos de fecha inválidos y rango invertido.
- 12 casos del BFF con su servicio y RestClient reales: parámetros, codificación,
  bearer token, límites individuales, omisión de filtros vacíos, permisos y errores.
  La respuesta del backend se simula, sin llamar a servidores externos.
- La pantalla Audit.jsx pasa ESLint y el frontend compila con npm run build.
  Vite advierte que el paquete principal supera 500 kB; dividir su carga queda pendiente.

Desde cada módulo, con Java 17 y Maven:

```powershell
# Backend
.\mvnw.cmd '-Dtest=Reservation*Tests,Audit*Tests' test

# BFF
.\mvnw.cmd '-Dtest=ReservationSecurityTests,AuditProxyTests' test

# Frontend, después de npm ci
.\node_modules\.bin\eslint.cmd src/pages/Audit.jsx
npm.cmd run build
```

Se usó Maven 3.9.15 disponible en la caché local; el wrapper del proyecto todavía
debe normalizarse como parte del arranque reproducible.

No se modificaron el respaldo SQL, datos de MySQL ni la estructura de tablas.
Falta verificar este recorrido en el navegador con el login real y el sistema local.
No se ejecutaron las pruebas de contexto completo que requieren servicios externos.
Copias de los archivos anteriores: backup/code-step-03-audit.

## Lo que sigue pendiente, en orden de trabajo

1. Hacer reproducible el arranque local: compilar imágenes desde estos fuentes,
   configurar las URLs del frontend/BFF y separar configuración por entorno.
   Gestionar contraseñas y la clave privada incluida en clave fuera del código y
   sus respaldos. Normalizar el wrapper de Maven.
2. Verificar disponibilidad y bloqueos en MySQL sobre una copia, revisar reservas
   antiguas que se crucen y controlar las migraciones del esquema antes de desplegar.
3. Hacer fiable la mensajería: el envío a Kafka/RabbitMQ y el commit de la reserva
   todavía pueden separarse. Se necesita registrar los eventos pendientes en la
   misma transacción y publicarlos con reintentos e identificación de duplicados.
4. Completar la gestión del catálogo y revisar los cálculos de paneles/reportes.
   La pantalla de catálogo actual consulta unidades; faltan las operaciones de gestión.
5. Completar la experiencia de reservas: mostrar mensajes legibles, gestionar la
   asignación explícita de reservas de recepción a huéspedes, paginar listados y
   unificar fechas/zonas horarias. Aplicar también paginación a la auditoría.

## Referencia para las pruebas del cliente HTTP

[MockRestServiceServer de Spring](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html).
