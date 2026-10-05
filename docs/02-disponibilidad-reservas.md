# Paso 2: fechas, disponibilidad y confirmaciones simultáneas

Actualización: los bloqueos se verificaron también con MySQL en [el paso 4](04-entorno-y-migraciones.md). Los productores ahora guardan eventos pendientes: una falla al guardar el evento revierte la transacción, mientras que una caída del broker permite conservar la reserva y reintentar el envío, como explica [el paso 5](05-eventos-confiables.md).

Antes se comprobaban los cruces únicamente al pasar de CREADA a CONFIRMADA, contra
otras reservas CONFIRMADA. La edición podía cambiar fechas o unidad sin validar y
dos solicitudes podían comprobar disponibilidad antes de que cualquiera guardara.

## Reglas aplicadas

- Nombre obligatorio de hasta 255 caracteres, identificador de unidad positivo y
  fechas de entrada y salida obligatorias. La salida debe ser posterior a la entrada.
  Las mismas comprobaciones se aplican al crear, editar y ocupar fechas al cambiar estado.
- Una unidad deshabilitada no admite altas, ediciones ni nuevas confirmaciones.
  Las reservas existentes pueden continuar su flujo, cancelar o finalizar su estadía.
- CONFIRMADA, CHECKIN_PENDIENTE y EN_ESTADÍA ocupan las fechas. CREADA, CANCELADA
  y CHECKOUT no bloquean nuevas solicitudes.
- La fecha de salida es un límite exclusivo. Una estadía del 10 al 12 permite otra
  desde el 12. El cruce se calcula como entrada < otra salida y salida > otra entrada.
- Al editar o cambiar estado, la consulta excluye la propia reserva.
- CREADA representa una solicitud: pueden existir solicitudes coincidentes mientras
  ninguna esté confirmada. Una vez que una confirma, las otras reciben 409 al intentar confirmar.
- Los estados y transiciones mantienen el flujo anterior; los valores vacíos o
  transiciones inválidas reciben 400 en lugar de producir errores por valores nulos.
- Una unidad con reservas asociadas no puede eliminarse, incluso si están canceladas
  o finalizadas, para preservar la referencia histórica.

## Cómo se evita la confirmación simultánea

Las escrituras usan una transacción con aislamiento READ_COMMITTED y bloqueos
PESSIMISTIC_WRITE. Al modificar una reserva se bloquea su fila; al comprobar
disponibilidad se bloquea la fila de la unidad hasta el commit o rollback.

Las solicitudes que quieren la misma unidad esperan su turno. Después de esperar,
la consulta de cruces ve los cambios confirmados por la transacción anterior.
El bloqueo pertenece a la base de datos y se comparte entre las instancias del backend.

Al cambiar de unidad se bloquean la anterior y la nueva en orden de identificador.
Así todas las operaciones solicitan esos bloqueos en el mismo orden. Los cambios
de disponibilidad y la eliminación de unidades usan el mismo bloqueo.

La comprobación ocurre antes de modificar los campos de una entidad administrada
por JPA. Esto evita que una validación fallida termine guardando datos por el mecanismo
automático de seguimiento de cambios. Reserva y auditoría se guardan en la misma
transacción de base de datos.

Se añadió un índice de unidad, estado y fechas para la consulta de cruces. Con el
ddl-auto=update actual, Hibernate lo incorporará al arrancar. No se modificaron los
datos existentes ni se resolvieron automáticamente reservas antiguas que ya se crucen.

## Respuestas de la API

| Código | Situación |
| --- | --- |
| 400 | Datos, fechas o transición de estado inválidos |
| 404 | Reserva o unidad inexistente |
| 409 | Fechas ocupadas, unidad deshabilitada o conflicto de bloqueo |
| 502 en el BFF | Backend inaccesible o error interno del backend |

El backend devuelve mensajes en el campo detail de un ProblemDetail. El BFF conserva
el código y cuerpo de los errores 4xx para que un conflicto de disponibilidad llegue
a la interfaz como 409. Los errores internos se reemplazan por un mensaje general.

## Verificación y límites

Resultado: 94 pruebas aprobadas, sin fallos ni errores: 74 en el backend (incluidos
31 casos de integración con JPA, concurrencia y rollback) y 20 en el BFF.

Las pruebas usan repositorios JPA reales y H2 en modo MySQL con dos hilos y dos
transacciones. La primera confirmación se mantiene abierta mientras la segunda
espera; al liberar el bloqueo se verifica que solo una confirma y que la otra recibe 409.
También se prueban fechas contiguas, todos los estados que ocupan, edición, cambio
de unidad, datos incompletos, permisos del paso 1 y rollback de reserva y auditoría.

H2 solo es una dependencia de pruebas. Los servicios de mensajería están simulados
y no se toca MySQL ni el respaldo SQL del proyecto. Docker estaba detenido durante
esta revisión; falta ejecutar la prueba de concurrencia con MySQL y comprobar la
actualización del esquema sobre una copia antes del despliegue.

Las transacciones de MySQL no convierten los envíos a RabbitMQ y Kafka en una operación
atómica con la base de datos. Su entrega fiable necesita un paso posterior de mensajería
(por ejemplo, un registro de eventos pendientes dentro de la transacción).

Desde cada módulo, con Java 17 y Maven:

```powershell
# Backend: permisos, validaciones y concurrencia
.\mvnw.cmd '-Dtest=Reservation*Tests' test

# BFF: permisos y propagación de errores
.\mvnw.cmd '-Dtest=ReservationSecurityTests' test
```

Copias de los archivos anteriores: backup/code-step-02-availability.

## Referencias

- [Bloqueos en Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
- [Bloqueos y liberación de transacciones en MySQL](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html).
- [Lecturas consistentes y READ_COMMITTED en MySQL](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html).
