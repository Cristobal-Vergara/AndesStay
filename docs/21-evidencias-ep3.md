# Paso 21: evidencias de EP3 y revisión de permisos

## Qué revisamos

Se contrastó el encargo EP3 con el código activo, las aserciones de sus pruebas y
los resultados disponibles. El resultado organiza las evidencias para explicar
AndesStay y preparar su entrega. Las pruebas se indexan en [TESTS.md](../TESTS.md).

La pauta contiene **ocho indicadores de RabbitMQ**, que suman el 100% del encargo.
Sus instrucciones generales también piden compilación, frontend funcional,
IDaaS/JWT, base de datos cloud y entrega de código por GitHub. Una nota final la
determina el docente; esta matriz registra implementación y comprobaciones.

Fuente: `EP3_DSY1107_Estudiante_encargo.pdf`, proporcionado por el usuario y
consultado localmente. Tiene siete páginas PDF, incluida la portada. Su SHA-256
es `28d76b79e6a1a1c22a63e890f75cdd25253099fb82756256da36b5cd2c7d9e9f`.
La copia original permanece fuera del repositorio.

## Matriz de indicadores evaluados

| ID | Indicador y peso | Aplicación en AndesStay | Evidencia y estado técnico |
|---|---|---|---|
| EP3-I01 | Nombres y rutas centralizados · 12% | `config/rabbit-topology.properties` compartido por backend y Rabbit Admin; nombres inyectados en configuración | TEST-MQ-001; pruebas del catálogo, beans y exportador. Implementado y comprobado localmente |
| EP3-I02 | Beans Queue, Exchange y Binding · 13% | Colas de recepción, huésped y cuarentena; exchange direct y cinco bindings de eventos de reservas | TEST-MQ-001 y TEST-MQ-002; configuración y enrutamiento real. Implementado para los casos de negocio |
| EP3-I03 | Configuración separada del negocio · 10% | `RabbitMQConfig`, `RabbitDemoConfig` y `RabbitDeliveryConfig`; productores/servicios reciben componentes configurados | TEST-MQ-001 y TEST-MQ-002; revisión de responsabilidades y pruebas de integración |
| EP3-I04 | Consumidores por dominio · 15% | `messaging/notifications` para recepción/huéspedes; `messaging/demo` para la prueba aislada, usando `@RabbitListener` | TEST-MQ-002; cada audiencia tiene su cola, consumidor y deduplicación |
| EP3-I05 | ACK y errores explícitos · 20% | ACK después del commit; errores permanentes, reintentos temporales, NACK y DLQ; logs de fallo | TEST-MQ-003; pruebas de handler, persistencia, broker real y recuperación |
| EP3-I06 | API REST de administración · 13% | CRUD `/api/v1/rabbitmq/queues`, `/exchanges` y `/bindings`; rol Admin y Location canónicas | TEST-ADMIN-001; recorrido HTTP completo con RabbitMQ real repetido en este paso |
| EP3-I07 | Administración encapsulada en servicio · 10% | Controller → `RabbitAdminService` → `RabbitManagementClient`; alcance operativo en `RabbitResourceScope` | TEST-ADMIN-001; pruebas de servicio y CRUD real |
| EP3-I08 | Validación de entradas · 7% | DTOs con `@Valid`, nombres, tipos, TTL, límites, referencias y borrado condicionado | TEST-ADMIN-002; entradas inválidas, recursos protegidos y errores comprobados |

Las notificaciones de reservas son el caso asíncrono elegido: recepción recibe
el ciclo de la reserva; el huésped recibe confirmaciones y cancelaciones propias.
Tickets o documentos son ejemplos del encargo, no casos adicionales obligatorios.
La demostración de errores es auxiliar: publica por el exchange predeterminado
del broker y tiene beans explícitos para su cola y su ruta de cuarentena.

Los nombres compartidos se almacenan en `.properties` y se consumen desde clases
`@Configuration`. Es la forma aplicada de centralización en este proyecto.

## Requisitos generales y pendientes

| ID | Requisito | Estado y evidencia |
|---|---|---|
| EP3-G01 | Backend Java/Spring Boot con varios servicios | Backend de negocio, BFF, Rabbit Admin y Kafka Admin; pruebas y compilaciones disponibles. Los consumidores de negocio están dentro del backend |
| EP3-G02 | Frontend completo y modular | React con páginas funcionales; 68 pruebas y ESLint aprobados en este paso. La construcción de producción pasó en el paso 19 |
| EP3-G03 | Login IDaaS y JWT en las llamadas | MSAL/Microsoft Entra; cliente HTTP envía bearer; pruebas de interfaz/proxy y verificaciones HTTP. Alta externa completa pendiente: TEST-AUTH-003 |
| EP3-G04 | JWT validado y permisos en backend | Resource Server en BFF/backend; reglas por rol y propiedad. TEST-AUTH-001 y TEST-AUTH-002; las pruebas de filtros simulan el decoder, y se declara ese límite |
| EP3-G05 | Base de datos cloud correctamente conectada | **Pendiente**. MySQL local, entidades, repositorios y Flyway están comprobados; todavía falta una conexión cloud y su evidencia: TEST-DB-002 |
| EP3-G06 | Gitignore y entrega del código actualizado | Exclusiones revisadas. Hay cambios locales pendientes de publicar; el remoto está configurado. TEST-DELIVERY-001 |
| EP3-G07 | Formato de frontend | El texto pide Angular. React es la alternativa elegida con la autorización de alternativas que informó el usuario; se documenta esa decisión sin cambiar de framework |

Los endpoints Admin operan sobre recursos con prefijo `andesstay.ops.`. La cola
de reservas se puede consultar y está protegida frente al CRUD destructivo.
Esta restricción permite demostrar creación y eliminación sin borrar datos de
negocio. El test real crea y elimina exclusivamente `andesstay.ops.verify.*`.

El laboratorio quorum, el benchmark, los controles de consumidores y los límites
HTTP amplían las guías estudiadas. Sus evidencias son complementarias a los ocho
indicadores. La aplicación habitual continúa con un broker de un nodo.

## Matriz de permisos vigente

La API de negocio aplica estas reglas. La lectura de operaciones del día para
recepción se presenta en el dashboard; la página de reportes corresponde a Admin.

| Operación | Admin | Recepcionista | Huesped | Auditor |
|---|---|---|---|---|
| Consultar/crear reservas | Sí | Sí | Propias | No |
| Editar, borrar, asignar propietario o cambiar estado | Sí | Sí | No | No |
| Consultar catálogo | Sí | Sí | Sí | No |
| Gestionar catálogo | Sí | Sí | No | No |
| Operaciones del día | Sí | Sí | No | No |
| KPIs y página de reportes | Sí | No | No | No |
| Auditoría | Sí | No | No | Sí |
| Avisos de reservas | Recepción | Recepción | Propios | No |
| Estado de mensajería/listeners | Sí | Consulta | No | No |
| Pausar/reanudar y demostración de error | Sí | No | No | No |
| Rabbit Admin y Kafka Admin | Sí | No | No | No |

La identidad se obtiene del JWT verificado mediante `tid:oid`. Un nombre o correo
enviado en un formulario no asigna otra identidad ni concede Admin. Una cuenta
delegada sin roles puede obtener Huesped después de registrar su propio perfil;
un Auditor conserva su permiso asignado. Las reservas ajenas se ocultan al huésped.

Se añadieron 16 pruebas de rutas: cuatro roles frente a reservas, catálogo,
reportes y auditoría. Renderizan `AccountProvider` y las rutas reales de React.
Comprueban enlaces visibles, acceso directo por URL, redirección y ausencia de
la llamada restringida. El ID token del fixture anuncia Admin mientras el
perfil simulado devuelve el rol de cada caso: la pantalla debe seguir el permiso
efectivo del servidor. MSAL y HTTP están simulados; no son sesiones Microsoft reales.

## Corrección encontrada durante la revisión

`BUG-LOCATION-001`: la prueba externa de Rabbit Admin esperaba Location con
`/api/rabbitmq`, conservada del contrato anterior. La API del paso 18 devuelve
Location con `/api/v1/rabbitmq`. Se ejecutó antes del cambio y falló exactamente
por esa diferencia.

Se actualizó el test para ejecutar el recorrido por v1 y verificar Location de
colas, exchanges y bindings. Sus GET posteriores comprueban que los recursos
creados se pueden consultar. El CRUD real volvió a pasar y eliminó sus recursos
temporales. Los alias conservan su cobertura en `RabbitAdminApiTests`.

La implementación de producción conserva su comportamiento. Los cambios de
código de este paso se limitan a pruebas; se añade la matriz y su índice.

## Cómo mostrarlo en una presentación

1. Abre el catálogo compartido y `RabbitMQConfig`: explica colas, exchange,
   routing keys y los bindings de recepción y huésped.
2. Sigue una creación o cambio de estado en el código: productor → outbox →
   `BrokerGateway` → RabbitMQ → consumidor → inbox y aviso → ACK.
3. Muestra la separación de paquetes y `RabbitDeliveryHandler`: explica un fallo
   permanente, los tres intentos de uno temporal y la cuarentena.
4. Ejecuta `scripts/test-rabbit-admin.cmd`: demuestra CRUD, validación, permisos,
   políticas y eliminación condicionada con el broker real.
5. Usa el dashboard con una cuenta Admin existente para mostrar controles de
   avisos y la demostración de error; observa la DLQ y los logs.
6. Presenta las pruebas por rol y las evidencias de pausa, benchmark y cluster.
   Identifica en cada caso qué sistemas son reales y cuáles se simulan.
7. Completa y registra los pendientes generales antes de la entrega final.

Los comandos se ejecutan desde la raíz del repositorio:

```powershell
.\scripts\start-local.cmd
.\scripts\test-local.cmd
.\scripts\test-rabbit-admin.cmd
.\scripts\test-listener-control.cmd
.\scripts\test-api-gateway.cmd
.\scripts\test-rabbit-cluster.cmd
```

Para MySQL y brokers reales existe `scripts/test-mysql.cmd`, que necesita la copia
local de `backup/andesstay.sql`. El benchmark se repite con
`scripts/test-rabbit-benchmark.cmd`. No es necesario repetir las pruebas de carga
para cada consulta de esta matriz. Las condiciones y efectos de cada comando se
documentan en [TESTS.md](../TESTS.md) y en sus pasos correspondientes.

## Registro externo y recorridos reales

El flujo de Microsoft y el botón de alta se verificaron en el paso 9. Falta
completar la autenticación con una identidad externa nueva y su perfil. Esa
participación corresponde al usuario que recibe el código o inicia sesión.

Procedimiento para TEST-AUTH-003:

1. Abre `http://localhost:5173/register` con una identidad externa de prueba nueva.
2. Inicia el alta mediante Microsoft y completa personalmente la autenticación.
3. Guarda tu nombre en AndesStay. Verifica que aparece el acceso de Huesped.
4. Consulta catálogo, crea una reserva con disponibilidad válida y consulta esa
   reserva desde la misma cuenta. Es una reserva de prueba real; identifica su ID.
5. Actualiza la página y confirma que conserva el perfil y sus reservas.
6. Comprueba el bloqueo de reportes, auditoría y cambios de estado. Las pruebas
   automáticas complementan el aislamiento entre dos identidades diferentes.

Para TEST-DELIVERY-002 usa cuentas de prueba que ya tengan sus roles asignados:
Admin gestiona y controla consumidores; Recepcionista gestiona reservas y consulta
operaciones; Huesped consulta solo lo propio; Auditor consulta auditoría. Registra
el rol, la operación, el resultado y la fecha, sin incluir JWT ni códigos en
evidencias compartidas. Las capturas ficticias del paso 9 conservan su etiqueta
de simulación y no cierran el alta externa.

## Verificación de esta etapa: 13 de octubre de 2026

| Ejecución realizada | Resultado |
|---|---|
| Frontend, incluida matriz de rutas | 68 aprobadas; nueve archivos de pruebas |
| ESLint del frontend | Correcto |
| BFF, suite normal | 78 aprobadas |
| Rabbit Admin, suite normal | 53 aprobadas; una externa omitida |
| Rabbit Admin con RabbitMQ real | Una aprobada; recursos de prueba eliminados |
| Exportador/catálogo RabbitMQ | Tres escenarios aprobados |

Se conservaron las evidencias anteriores con sus fechas: 245 pruebas normales
del backend del paso 20, 34 de MySQL/brokers del 8 de octubre y la prueba de
cluster del paso 20. Las cifras no representan una nueva ejecución de todas esas
suites en esta etapa. El registro distingue estas ejecuciones.

Las salidas de este paso están en `.validation/step-23-*.log`. El informe
compartible está en `.ecc/evidence/ep3-review-latest.json`. El respaldo previo
está en `backup/code-step-23-ep3-evidence/`, excluido de Git.

Los siguientes cierres son el alta externa y los recorridos reales por rol,
la conexión cloud de MySQL y la publicación de la versión revisada en GitHub.
El registro incluye además un contrato API compartido como mejora futura;
no se añade como un requisito de la pauta de EP3.

Al terminar la revisión se verificaron seis servicios locales saludables,
login y registro HTTP 200 y reservas HTTP 401 sin JWT. El resumen está en
`.validation/step-23-runtime-verification.json`. No se reconstruyeron imágenes
de producción. La preparación de los documentos y el informe final continuó
el 14 de octubre; las ejecuciones anteriores conservan sus fechas y resultados.
