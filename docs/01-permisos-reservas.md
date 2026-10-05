# Paso 1: permisos y propiedad de reservas

Actualización: la asignación explícita a huéspedes y la lectura del catálogo por Huesped se explican en [el paso 6](06-catalogo-reportes-interfaz.md). Este documento conserva la explicación y las verificaciones del primer paso.

El backend decide quién puede acceder a las reservas. El BFF aplica los mismos permisos
para rechazar las solicitudes antes de llamar al backend.

| Operación | Admin | Recepcionista | Huesped | Auditor |
| --- | --- | --- | --- | --- |
| Crear reserva | Sí | Sí | Sí | No |
| Consultar reservas | Todas | Todas | Solo propias | No |
| Editar, borrar o cambiar estado | Sí | Sí | No | No |

## Qué se cambió y por qué

- Se separaron las reglas por método HTTP: consultar con GET o crear con POST no
  concede permiso para editar con PUT o borrar con DELETE.
- Se añadió owner_id a Reservation. Su valor combina tid y oid del JWT validado:
  identifica la organización y el usuario de Microsoft sin depender del nombre o correo.
  Fuente: https://learn.microsoft.com/en-us/entra/identity-platform/claims-validation
- El huésped consulta mediante findByOwnerId o findByIdAndOwnerId. Una reserva ajena
  responde 404, igual que una inexistente, sin revelar su existencia.
- El cuerpo JSON no puede asignar owner_id. El servidor ignora cualquier ID o estado
  de alta enviado por el cliente: genera una reserva nueva y comienza en CREADA.
- Las operaciones de gestión también comprueban el rol en el servicio, para proteger
  llamadas internas además de las solicitudes HTTP.
- El BFF usa sesiones STATELESS porque cada solicitud se autentica con su bearer token.

## Reservas existentes y creadas por recepción

owner_id es nullable para incorporar el campo sin inventar propietarios de reservas
existentes. Con la configuración actual ddl-auto=update, Hibernate añade la columna
al arrancar; el despliegue debe probarse sobre una copia de la base de datos.

Las reservas sin propietario solo las consulta el personal. Una reserva nueva se
asocia al usuario autenticado que la crea, también si trabaja en recepción.
La asociación de una reserva de recepción con la cuenta del huésped necesita una
operación explícita futura. No se infiere del nombre ni del historial de auditoría.

## Verificación

Las pruebas de seguridad HTTP ejercitan el filtro y el conversor de roles usando un
JwtDecoder simulado. Las pruebas del servicio verifican aislamiento por propietario,
permisos de gestión e intento de inyectar ID, propietario y estado al crear.

Desde cada módulo, con Java 17 y Maven:

- Backend: mvnw.cmd -Dtest=ReservationSecurityTests,ReservationServiceSecurityTests test
- BFF: mvnw.cmd -Dtest=ReservationSecurityTests test

Estas pruebas no requieren Microsoft, MySQL, Kafka ni RabbitMQ en ejecución. No
sustituyen la comprobación del login real ni la prueba de actualización del esquema.

## Próximos pasos

1. Validar disponibilidad y cambios de fechas; impedir confirmaciones simultáneas.
   Implementado en [el paso 2](02-disponibilidad-reservas.md); falta verificarlo en MySQL.
2. Reenviar filtros y respuestas de error del BFF.
   Implementado para auditoría en [el paso 3](03-filtros-auditoria.md);
   los errores de reservas se corrigieron en el paso 2.
3. Hacer reproducible el arranque local y proteger configuraciones y claves.
4. Completar las operaciones de catálogo, paneles y experiencia de usuario.
