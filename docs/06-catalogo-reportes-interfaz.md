# Paso 6: catálogo, reportes e interfaz

## Catálogo

Admin y Recepcionista pueden crear, editar, habilitar/deshabilitar y eliminar unidades. Huesped puede consultar el catálogo para escoger una unidad. Auditor no tiene acceso al catálogo ni a las reservas.

El servidor exige nombre/tipo no vacíos, hasta 255 caracteres, capacidad entera positiva, tarifa positiva con un máximo de dos decimales y disponibilidad explícita. La descripción admite hasta 255 caracteres. Normaliza espacios y escala del precio después de validar.

Crear una unidad ignora un ID enviado por el cliente. Editar y eliminar conservan los bloqueos de disponibilidad del paso 2. Una unidad referenciada por reservas devuelve 409 al intentar eliminarla: conserva las referencias y el historial. Puede deshabilitarse para impedir nuevas solicitudes.

El backend rechaza capacidad fraccionaria al deserializar JSON, evitando convertir silenciosamente 2.5 en 2. El formulario valida antes de enviar y el servidor vuelve a comprobar los datos.

## Listas paginadas

Las listas de reservas, catálogo y auditoría reciben `page` y `size`: página desde cero, tamaño por defecto 20 y máximo 100. Una página negativa o un tamaño fuera de 1–100 devuelve 400.

La respuesta contiene:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Este contrato sustituye los arrays anteriores. BFF e interfaz se actualizaron juntos. Otros clientes de estas listas también deben leer `content`. Reservas se ordenan por ID descendente; catálogo por ID; auditoría por fecha e ID descendentes.

Para huéspedes se aplica el propietario en la consulta antes de contar y paginar, por lo que ni los resultados ni los totales incluyen reservas ajenas. El filtro de auditoría se conserva al cambiar de página. Limpiar vuelve a la página cero sin filtros.

## Reservas creadas por recepción

Una reserva creada por el personal permanece accesible para el personal; no se adivina la cuenta del huésped mediante su nombre.

El huésped puede consultar y copiar su OID en “Mis reservas”. Recepción lo utiliza para asignar una reserva con `PUT /api/bff/reservations/{id}/owner` y cuerpo `{"objectId":"UUID"}`. El backend valida el UUID, usa la organización del token de recepción y registra `PROPIETARIO_ASIGNADO` en auditoría.

La cuenta debe pertenecer a esa organización. El servidor no consulta Microsoft Graph ni confirma que el OID introducido exista: recepción debe utilizar el que comparte el huésped. Una asignación incorrecta puede corregirse asignando el OID correcto y queda auditada.

El campo `guestAssigned` es informativo y de solo lectura en JSON. El propietario completo no se expone. Los clientes no pueden elegirlo al crear o editar. El huésped puede crear solicitudes y leer sus reservas; el personal conserva la confirmación, edición, estados y eliminación.

## Cálculos de reportes

- Total de reservas y estados: registros actuales agrupados por estado.
- Ocupación activa: unidades distintas del catálogo con reservas EN_ESTADÍA. Dos estadías en una misma unidad cuentan como una unidad ocupada.
- Porcentaje: unidades ocupadas dividido entre todas las unidades del catálogo, multiplicado por 100. Las deshabilitadas siguen formando parte de la capacidad total; “Unidades habilitadas” se presenta por separado.
- Reservas por unidad: conteo agrupado, incluyendo cero para unidades sin reservas. Identificadores de unidades antiguas ausentes pueden aparecer en el historial.
- Ciclo: minutos desde la primera creación registrada hasta la primera confirmación posterior o igual. Ignora confirmaciones anteriores, duplicados y reservas sin ciclo completo.
- Promedio de ciclo: usa los ciclos completos del historial de auditoría, incluso cuando una reserva fue eliminada; muestra cuántos ciclos respaldan el promedio.
- Creaciones por hora: primera creación de cada reserva, con las 24 horas representadas, incluyendo ceros.

Los conteos agrupados y una sola lectura del historial sustituyen consultas por cada reserva/unidad. La transacción de reportes usa lectura repetible para que sus consultas compartan una vista consistente.

## Fecha operativa

`APP_TIME_ZONE` es `America/Santiago` por defecto. Auditoría nueva y fecha operativa usan un Clock con esa zona. Outbox/inbox almacenan instantes en UTC.

`GET /api/bff/reports/operations?date=2026-10-03` devuelve llegadas CONFIRMADA/CHECKIN_PENDIENTE y salidas EN_ESTADÍA de ese día, ordenadas por unidad/ID. Si no se indica fecha, usa el día de Chile, incluso cerca del cambio de día UTC. Solo Admin y Recepcionista pueden consultarlo.

Las fechas de entrada/salida son fechas civiles y la pantalla no las convierte mediante UTC. Los timestamps antiguos de auditoría carecen de información de zona; no se reinterpretaron ni modificaron durante la migración.

## Interfaz y errores

Catálogo ofrece formularios y acciones según el rol. Reservas incluye creación, edición, asignación, eliminación y únicamente las transiciones de estado permitidas. El panel muestra operaciones para recepción, indicadores para Admin y reservas propias para Huesped.

Las llamadas comparten adquisición del token y tratamiento de errores. Los 400/404/409 conservan la explicación del backend; los 5xx se muestran con un mensaje general. Las solicitudes HTTP tienen un límite de 20 segundos, cancelación al abandonar la pantalla y estado visible de carga. BFF tiene límites de conexión y lectura.

Las páginas se cargan bajo demanda y React/MSAL se agrupan por separado, manteniendo el orden de ejecución de los módulos. Se verificaron 13 pruebas del frontend, ESLint y la construcción de producción. El navegador cargó el inicio de sesión sin errores de consola; los archivos estáticos y rutas profundas respondieron correctamente.

La entrada con cuentas reales de Microsoft y la asignación de roles en Entra quedan como validación manual del entorno externo.
