# Pruebas y evidencias de AndesStay

Este índice relaciona requisitos y riesgos con evidencia ejecutable. El código
de pruebas conserva las aserciones; la [matriz de EP3](docs/21-evidencias-ep3.md)
identifica los requisitos de la pauta. No se calcula una calificación a partir
del número de pruebas.

Las fuentes EP3-Ixx se consultan en [los indicadores](docs/21-evidencias-ep3.md#matriz-de-indicadores-evaluados)
y EP3-Gxx en [los requisitos generales](docs/21-evidencias-ep3.md#requisitos-generales-y-pendientes).
Las reglas de permisos están en [la matriz vigente](docs/21-evidencias-ep3.md#matriz-de-permisos-vigente).
Los nombres de nodo Java del registro se buscan en los paquetes `com.example.AndesStay`
(backend), `com.example.andesstay.AndesStayBFF` (BFF), `com.example.AndesStayRabbitAdmin`
y `com.example.AndesStayKafkaAdmin`, dentro de sus carpetas `src/test/java`.

Estados: `covered` = comportamiento comprobado dentro del límite indicado;
`unverified` = existe procedimiento/evidencia parcial; `missing` = falta la
comprobación requerida. Un test con Microsoft simulado no cierra un recorrido
E2E real de Microsoft. No hay un umbral de cobertura configurado en el proyecto;
no se atribuye un porcentaje de cobertura sin medirlo.

## Entradas de ejecución

Todos los comandos parten de la raíz del repositorio. Los módulos Java usan
JDK 17 y su wrapper Maven; el frontend usa Node 24 y dependencias instaladas.

| Grupo | Comando | Dependencias y cuándo ejecutarlo |
|---|---|---|
| NORMAL | `scripts/test-local.cmd` | Todas las suites normales, catálogo, ESLint y build; sin servicios externos. Antes de entregar cambios |
| TOPOLOGY | `powershell -NoProfile -File scripts/test-rabbit-topology.ps1` | Generación y validación de nombres/políticas; al cambiar el catálogo/exportador |
| MYSQL-BROKERS | `scripts/test-mysql.cmd` | MySQL/Kafka/RabbitMQ locales, `.env` y respaldo privado `backup/andesstay.sql`; pruebas con esquemas y recursos temporales |
| ADMIN-REAL | `scripts/test-rabbit-admin.cmd` | RabbitMQ local y `.env`; al cambiar CRUD, validación, versiones o políticas |
| LISTENERS-REAL | `scripts/test-listener-control.cmd` | RabbitMQ local y `.env`; al cambiar pausa, ACK o reanudación |
| BENCHMARK | `scripts/test-rabbit-benchmark.cmd` | RabbitMQ local y `.env`; medición deliberada, no comprobación rutinaria de cada cambio |
| CLUSTER-REAL | `scripts/test-rabbit-cluster.cmd` | Docker Linux, Node y Java; tres nodos propios, credenciales en memoria y H2 |
| GATEWAY-REAL | `scripts/test-api-gateway.cmd` | Docker Linux y Node; Nginx real y BFF de prueba propios |
| MANUAL-ID | Procedimiento del paso 21 | Microsoft, navegador y participación del titular; alta externa y cuentas con roles existentes |
| CLOUD-DB | Pendiente | MySQL cloud y conexión protegida; proveedor/configuración aún pendientes |
| DELIVERY | `git status --short` y verificación de la versión publicada | Revisión de exclusiones, commit publicado y procedimiento de arranque |

Las pruebas externas tienen flags específicos y quedan omitidas en NORMAL.
ADMIN-REAL usa `andesstay.ops.verify.*`; MYSQL-BROKERS usa esquemas
`andesstay_test_*`; los demás grupos usan sus recursos temporales documentados.
CLUSTER-REAL verifica etiquetas antes de controlar nodos y eliminar volúmenes.
GATEWAY-REAL y CLUSTER-REAL usan un archivo Compose de entorno vacío para evitar
cargar el `.env` de la aplicación.

## Inventario de activos

Inventario del 13 de octubre: 44 archivos Java de pruebas (backend 26, BFF 10,
Rabbit Admin 6, Kafka Admin 2) y nueve archivos de pruebas de React. Se agregan
por responsabilidad; no se reproduce el nombre de cada test en este índice.

Los niveles usan `unit`, `integration`, `contract`, `E2E` y `smoke`. Los propósitos
usan `rule protection`, `critical path`, `regression protection` y
`specialized protection`.

| Activo | Nivel | Propósito | Grupo | Real/simulado y ubicación | Evaluación |
|---|---|---|---|---|---|
| Reglas, perfiles y transacciones | integration | rule protection | NORMAL | Servicios/JPA reales, H2; `AndesStay Backend/src/test/java/.../service` | required |
| Seguridad HTTP del backend | integration | rule protection | NORMAL | Filtros reales, JwtDecoder/repositorios controlados; `.../config/*SecurityTests` | required |
| Catálogo y beans RabbitMQ | integration | specialized protection | NORMAL, TOPOLOGY | Configuración/exportador reales, nombres temporales; `RabbitTopologyContractTests` y script | required |
| Autorización y proxy BFF | integration | critical path | NORMAL | Filtros/RestClient reales; JWT y servidor HTTP de destino de prueba; `AndesStayBFF/src/test/java` | required |
| CRUD y DTOs Rabbit Admin | unit, integration | rule protection, regression protection | NORMAL | Servicio/controlador reales; client/decoder controlados; `AndesStayRabbitAdmin/src/test/java` | required |
| Operaciones Kafka Admin | integration | rule protection | NORMAL | Controlador/filtro real; cliente/decoder de prueba; `AndesStayKafkaAdmin/src/test/java` | required |
| Páginas, perfiles, rutas y acciones React | integration | critical path | NORMAL | Componentes/router reales; MSAL/fetch simulados; `AndesStay Frontend/src/test` | required |
| MySQL, migraciones y mensajería | integration | critical path | MYSQL-BROKERS | MySQL/Kafka/RabbitMQ reales; identidades de prueba; tres clases especializadas del backend | required |
| CRUD Rabbit Admin real | integration | critical path, regression protection | ADMIN-REAL | API Spring y broker reales; JwtDecoder de prueba; `RealRabbitAdminIntegrationTests` | required |
| Pausa de consumidores | integration | specialized protection | LISTENERS-REAL | RabbitMQ y listeners reales, mensajes sintéticos; `ListenerControlBrokerTests` | required |
| Rendimiento | integration | specialized protection | BENCHMARK | Broker real, trabajo sintético; `RabbitThroughputBrokerTests` | required |
| Cluster y deduplicación | integration | specialized protection | CLUSTER-REAL | Tres brokers y servicios de recepción reales; H2; `RabbitClusterRecoveryTests` | required |
| Límites HTTP | smoke, integration | specialized protection | GATEWAY-REAL | Nginx real y BFF stub; `scripts/test-api-gateway.mjs` | required |
| Alta Microsoft y cuatro roles en navegador | E2E | critical path | MANUAL-ID | Recorrido completo con identidades reales | missing |
| Conexión MySQL cloud | integration | critical path | CLOUD-DB | Servicio cloud aún sin configurar | missing |
| Contrato API compartido formal | contract | specialized protection | Pendiente | No se encontró OpenAPI/JSON Schema común de frontend/BFF/backend | missing |

Resultado del inventario: 13 grupos existentes requeridos, tres activos
ausentes, cero candidatos a duplicados y cero activos desactualizados abiertos.
La expectativa antigua de Location fue un activo desactualizado detectado y
corregido en esta revisión; no se eliminó ni debilitó su prueba.

## Registro de puntos de prueba

Cada punto referencia su fuente y registra un input representativo, un resultado
observable y el nodo de prueba. Nivel, propósito y límite real/simulado se toman
de su activo y grupo anteriores. `covered` siempre se refiere a ese límite.

### TEST-MQ-001 — Catálogo y topología
- Estado: `covered`. Fuente: EP3-I01/I02/I03.
- Entrada: renombrar colas/exchanges en una copia del catálogo; generar políticas.
- Observable: beans y exportador usan los nombres; rechazan configuración vacía sin sobrescribir definiciones válidas.
- Nodos: `config.RabbitTopologyContractTests`, `RabbitTopologyControllerTests`, `scripts/test-rabbit-topology.ps1`. Grupos: NORMAL, TOPOLOGY.
- Evidencia: paso 12; suite backend del paso 20; `.validation/step-23-rabbit-admin-unit-tests.log`; tres escenarios del exportador ejecutados en esta revisión.

### TEST-MQ-002 — Audiencias y separación del negocio
- Estado: `covered`. Fuente: EP3-I02/I03/I04 y paso 13.
- Entrada: eventos de creación, confirmación y cancelación con identidad de huésped.
- Observable: recepción recibe tres avisos; huésped recibe sus confirmación/cancelación; otra identidad no los consulta.
- Nodos: `service.RealBrokerIntegrationTests`, `service.ReservationNotificationIntegrationTests`; revisión de `config` y `messaging/notifications`. Grupos: NORMAL, MYSQL-BROKERS.
- Evidencia: `.validation/mysql-tests.log` del 8 de octubre (34 aprobadas); `.validation/step-22-backend-tests.log` y paso 13. Kafka/RabbitMQ/MySQL reales en la suite especializada; autentificación controlada por el test.

### TEST-MQ-003 — ACK, errores, retry y DLQ
- Estado: `covered`. Fuente: EP3-I05 y paso 14.
- Entrada: fallo transitorio, evento inválido y pérdida de canal después de guardar el aviso.
- Observable: ACK después del commit; tres intentos para el fallo temporal; NACK/DLQ según causa; reentrega sin otro aviso.
- Nodos: `messaging.delivery.RabbitDeliveryHandlerTests`, `service.ReservationNotificationIntegrationTests`, `service.RealBrokerIntegrationTests`. Grupos: NORMAL, MYSQL-BROKERS.
- Evidencia: pasos 14/20, logs de backend del paso 20 y MySQL del 8 de octubre. El cluster complementa este punto en TEST-MQ-006.

### TEST-ADMIN-001 — CRUD REST y Location
- Estado: `covered`. Fuente: EP3-I06/I07, paso 15 y `BUG-LOCATION-001` del paso 21.
- Entrada: crear, consultar, actualizar y eliminar colas, exchanges y bindings por v1.
- Observable: códigos y Location canónicas; enrutamiento/políticas reales; recursos consultables; borrado condicionado y limpieza.
- Nodos: `RabbitAdminApiTests`, `RabbitAdminServiceTests`, `RealRabbitAdminIntegrationTests`. Grupos: NORMAL, ADMIN-REAL.
- Antes: `.validation/step-23-rabbit-admin-before-tests.log`, una aserción fallida por el prefijo anterior. Después: `.validation/step-23-rabbit-admin-after.log`, una prueba real aprobada; suite normal 53 aprobadas y una omitida.

### TEST-ADMIN-002 — Validación y protección de recursos
- Estado: `covered`. Fuente: EP3-I08 y paso 15.
- Entrada: nombres vacíos, TTL/límite fuera de rango, rol Huesped y borrado de una cola de negocio o en uso.
- Observable: 400/403/409 según causa y conservación del recurso protegido.
- Nodos: `RabbitAdminApiTests`, `RabbitAdminServiceTests`, `RealRabbitAdminIntegrationTests`. Grupos: NORMAL, ADMIN-REAL.
- Evidencia: logs normal/real del paso 21. Broker real en el recorrido especializado; decoder de prueba y credenciales del broker local privadas.

### TEST-AUTH-001 — Roles en los filtros
- Estado: `covered`. Fuente: EP3-G04 y matriz de permisos del paso 21.
- Entrada: solicitudes sin bearer, token inválido o rol no autorizado frente a cada operación.
- Observable: 401/403 y ausencia de llamada al servicio/destino protegido.
- Nodos: backend `config/*SecurityTests`; BFF `config/*ProxyTests` y `ReservationSecurityTests`; administradores `*ApiTests`. Grupo: NORMAL.
- Evidencia: `.validation/step-22-backend-tests.log`, `.validation/step-23-bff-tests.log` y suite normal de Rabbit Admin del paso 21. Los decoder simulados prueban autorización; la autenticación Microsoft real se registra aparte.

### TEST-AUTH-002 — Propiedad y alta de perfil
- Estado: `covered`. Fuente: pasos 1/9 y EP3-G04.
- Entrada: mismo JWT antes/después de registrar perfil; email/roles/owner falsos en body; segunda identidad o tenant.
- Observable: acceso básico Huesped solo para la identidad propia; no obtiene permisos del personal; no ve reservas/perfiles ajenos.
- Nodos: `UserProfileSecurityTests`, `PublicGuestSecurityTests`, `UserProfileIntegrationTests`, `ReservationServiceSecurityTests`, BFF `PublicGuestProxyTests`. Grupo: NORMAL.
- Evidencia: suite backend del paso 20 y BFF del paso 21. JPA/H2 real para perfiles y reglas; identidades JWT controladas.

### TEST-UI-001 — Rutas por permisos efectivos
- Estado: `covered`. Fuente: EP3-G02/G03 y matriz de roles del paso 21.
- Entrada: cuatro permisos efectivos frente a cuatro páginas, con Admin en el ID token del fixture.
- Observable: enlaces/acceso correctos; una URL prohibida redirige y no consulta su API; bearer en la consulta autorizada.
- Nodo: `AndesStay Frontend/src/test/role-routes.test.jsx`; complementos `public-access.test.jsx`, `registration.test.jsx`, `pages.test.jsx`. Grupo: NORMAL.
- Evidencia: `.validation/step-23-frontend-tests.log`, 68 aprobadas incluyendo 16 combinaciones; MSAL y fetch simulados.

### TEST-AUTH-003 — Alta externa Microsoft completa
- Estado: `unverified`. Fuente: EP3-G03 y paso 9.
- Entrada: correo externo nuevo, autenticación personal, perfil y reserva propia.
- Observable: Microsoft emite acceso válido, perfil persiste y Huesped puede consultar su reserva sin gestión del personal.
- Nivel/propósito: E2E / critical path. Grupo: MANUAL-ID. Nodo: procedimiento del paso 21.
- Evidencia parcial: portal y pantalla de alta documentados en paso 9; falta completar código/login y perfil con el titular. Capturas ficticias no cierran este punto.

### TEST-DB-001 — MySQL local y migraciones
- Estado: `covered`. Fuente: paso 4 y soporte local de EP3-G05.
- Entrada: esquema nuevo y copia privada del SQL anterior; reservas con solapamientos/concurrencia.
- Observable: Flyway conserva datos originales; entidades validan; reglas de disponibilidad mantienen sus invariantes.
- Nodos: `service.MySqlMigrationTests`, `service.ReservationAvailabilityIntegrationTests`. Grupo: MYSQL-BROKERS.
- Evidencia: `.validation/mysql-tests.log` del 8 de octubre y `.validation/mysql-migrations.json`. Son pruebas de MySQL local; TEST-DB-002 cubre el requisito cloud pendiente.

### TEST-DB-002 — Conexión cloud
- Estado: `missing`. Fuente: EP3-G05.
- Entrada: datasource cloud protegido con entidades, repositorios y migraciones de AndesStay.
- Observable: inicio con conexión válida, esquema migrado y lectura/escritura funcional.
- Nivel/propósito: integration / critical path. Grupo: CLOUD-DB. Nodo y evidencia: pendientes de proveedor y configuración.

### TEST-DELIVERY-001 — Versión publicada
- Estado: `missing`. Fuente: EP3-G06 e instrucciones de entrega.
- Entrada: checkout de la versión final publicada y configuración externa.
- Observable: código/docs presentes, dependencias privadas excluidas y arranque documentado reproducible.
- Nivel/propósito: smoke / critical path. Grupo: DELIVERY.
- Evidencia parcial: remoto GitHub configurado y exclusiones revisadas. Hay cambios locales posteriores al commit del 5 de octubre; falta publicar y comprobar esa versión. Este punto no autoriza enviar correo ni entregar en AVA.

### TEST-DELIVERY-002 — Recorridos reales por rol
- Estado: `unverified`. Fuente: EP3-G02/G03/G04 y matriz de roles del paso 21.
- Entrada: cuentas de prueba con Admin, Recepcionista, Huesped y Auditor ya asignados.
- Observable: cada rol completa su recorrido y recibe los bloqueos correspondientes.
- Nivel/propósito: E2E / critical path. Grupo: MANUAL-ID. Nodo: procedimiento del paso 21.
- Evidencia parcial: controles de Admin comprobados en el paso 16; automatización con identidades simuladas. Falta una evidencia completa de los cuatro recorridos con Microsoft real.

### TEST-MQ-004 — Pausa y reanudación
- Estado: `covered`. Fuente: paso 16 y guías adaptadas.
- Entrada: pausar recepción con entrega sin ACK y enviar otro evento.
- Observable: pausa pendiente, rechazo de reanudación anticipada, audiencia independiente y entrega posterior.
- Nodo: `service.ListenerControlBrokerTests`. Grupo: LISTENERS-REAL.
- Evidencia: `.validation/listener-control-real-tests.log`, una aprobada; comprobación visual Admin documentada en paso 16.

### TEST-MQ-005 — Comparación de rendimiento
- Estado: `covered`. Fuente: paso 17 y guías adaptadas.
- Entrada: lotes sintéticos con 1/3 consumidores y prefetch 1/10.
- Observable: IDs completos, ACK, tasas/tiempos observados y eliminación de recursos.
- Nodo: `service.RabbitThroughputBrokerTests`. Grupo: BENCHMARK.
- Evidencia: `.ecc/benchmarks/rabbitmq-latest.json` y paso 17. No determina capacidad HTTP ni incluye transacciones MySQL.

### TEST-MQ-006 — Recuperación quorum
- Estado: `covered`. Fuente: paso 20 y guías adaptadas.
- Entrada: caída del líder antes del ACK y pérdida de dos réplicas.
- Observable: elección/reconexión, reentrega sin duplicar, ausencia de confirmación sin mayoría y 21 avisos finales.
- Nodo: `service.RabbitClusterRecoveryTests`. Grupo: CLUSTER-REAL.
- Evidencia: `.ecc/evidence/rabbitmq-cluster-latest.json`; tres brokers reales, servicios de recepción reales y H2. Laboratorio en un PC; no equivale a una instalación distribuida entre máquinas.

### TEST-API-001 — Versionado y compatibilidad
- Estado: `covered`. Fuente: paso 18.
- Entrada: v1, alias, versión desconocida y permisos de varios roles.
- Observable: mismo contrato/autorización en alias; BFF reenvía JWT a v1; versión no implementada no llega al negocio.
- Nodos: backend `ApiVersioningTests`, BFF `ApiVersioningProxyTests`, Admin `RabbitAdminApiTests`, `KafkaApiVersioningTests`. Grupo: NORMAL.
- Evidencia: suites del paso 18 y BFF/Rabbit Admin del paso 21. HTTP real sin JWT en los pasos 18–20.

### TEST-API-002 — Límite del punto de entrada
- Estado: `covered`. Fuente: paso 19.
- Entrada: ráfaga que mezcla alias/métodos y cabeceras con IP falsas.
- Observable: 429 antes del BFF, Retry-After y recuperación; contenido estático accesible; cliente no reenvía un POST.
- Nodos: `scripts/test-api-gateway.mjs` y frontend `api.test.js`. Grupos: GATEWAY-REAL, NORMAL.
- Evidencia: `.validation/api-gateway-latest.json` (seis escenarios), pasos 19/20 y suite frontend del paso 21. Nginx real con BFF stub; Vite/directos quedan fuera de este gateway.

### TEST-API-003 — Contrato formal compartido
- Estado: `missing`. Fuente: riesgo de divergencia entre frontend/BFF/backend.
- Entrada: un único OpenAPI/JSON Schema compartido por proveedor y consumidor.
- Observable: validación automatizada de la misma forma serializada en ambos lados.
- Nivel/propósito: contract / specialized protection. Nodo/grupo: pendientes.
- Evidencia actual: pruebas de respuestas/proxy y topología compartida; no se encontró un contrato API formal común. Es una mejora futura y no un indicador añadido a EP3.

Resumen: 19 puntos; 14 `covered`, dos `unverified` y tres `missing`.

## Salidas manuales y acciones

| Prioridad | Punto | Participación o dependencia | Acción siguiente |
|---|---|---|---|
| Alta | TEST-AUTH-003 | El titular debe completar la autenticación/código Microsoft | Ejecutar el alta y conservar evidencia de perfil/reserva sin códigos ni JWT |
| Alta | TEST-DB-002 | Falta seleccionar/configurar el datasource cloud | Preparar conexión y migración aislada; comprobar lectura/escritura |
| Alta | TEST-DELIVERY-002 | Faltan cuentas/recorridos reales completos | Usar roles existentes y anotar cada resultado |
| Alta | TEST-DELIVERY-001 | Versión actual local sin publicar | Revisar diff/exclusiones, publicar y comprobar el checkout entregado |
| Posterior | TEST-API-003 | No hay contrato formal compartido | Incorporar esquema y conectar validación de ambos lados |

La participación manual cierra la identidad real; los componentes internos
conservan sus pruebas automáticas. El código/OTP pertenece al usuario. Una salida
manual pendiente conserva `unverified`, aunque sus componentes simulados pasen.

## Ejecuciones y mantenimiento

Este paso ejecutó frontend (68), BFF (78), Rabbit Admin normal (53 y una omitida),
Rabbit Admin real (una) y el exportador (tres escenarios), todos con exit code 0.
ESLint pasó. La reproducción anterior a corregir el test dio exit code 1 y queda
registrada. Las suites Java normales se verificaron por módulo; se precargó el
agente Mockito donde lo necesitó el entorno restringido de Windows.

El backend del paso 20 tuvo 245 aprobadas y seis externas omitidas. MySQL/brokers
tuvieron 34 aprobadas el 8 de octubre. El informe
`.ecc/evidence/ep3-review-latest.json` conserva fechas, alcance y huellas del código
revisado; no presenta estas evidencias anteriores como ejecuciones nuevas.

Al cambiar un contrato, permisos, consumidor, runner o prueba especializada,
revisa sus TEST-IDs y vuelve a ejecutar el grupo afectado. Antes de entregar,
reconcilia los pendientes y la versión publicada. Las nuevas salidas se añaden a
la historia del paso correspondiente; este índice mantiene el estado actual.
