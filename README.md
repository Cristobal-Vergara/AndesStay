# AndesStay

Aplicación de reservas con React, BFF y backend Spring Boot, MySQL, Kafka y RabbitMQ.

## Arranque local en Windows

Requisitos: Docker Desktop con contenedores Linux. Ejecuta desde esta carpeta:

```powershell
.\scripts\start-local.cmd
```

Abre http://localhost:5173. El script genera una sola vez las contraseñas de MySQL y RabbitMQ en `.env`, prepara `AndesStay Frontend/.env.local`, construye las aplicaciones desde su código y espera sus comprobaciones de salud. No imprime las contraseñas.

La primera construcción necesita Internet. Los contenedores usan usuarios sin privilegios para las aplicaciones, conservan los datos en volúmenes y publican sus puertos únicamente en localhost. El backend y el BFF se comunican dentro de Docker; Nginx sirve React y reenvía `/api` al BFF. Las unidades de ejemplo se insertan exclusivamente con el perfil `local`. El trabajo `rabbit-setup` importa las políticas de entrega y cuarentena; termina con `Exited (0)` antes de iniciar el backend. El inicio de Admin/Recepcionista muestra el estado real de entrega. Admin puede iniciar una prueba local aislada.

Para detener conservando los datos:

```powershell
.\scripts\stop-local.cmd
```

Para cambiar configuración, edita `.env` y vuelve a ejecutar el arranque. Cambiar una contraseña en el archivo no modifica automáticamente una cuenta ya creada dentro de un volumen.

Los nombres y rutas RabbitMQ se definen una sola vez en `config/rabbit-topology.properties`, compartido por backend y Rabbit Admin. El arranque genera las políticas del broker en `.validation/rabbitmq-definitions.json` desde ese catálogo. Consulta el [paso de centralización](docs/12-topologia-rabbitmq-centralizada.md).

Los consumidores RabbitMQ se agrupan en `messaging/notifications` (recepción y huéspedes) y `messaging/demo` (prueba de entrega). Cada uno escucha su propia cola y delega el efecto en los servicios. El [paso 13](docs/13-consumidores-por-dominio.md) explica la organización y la deduplicación por audiencia.

Los tres listeners usan ACK manual después del commit de su efecto. Los errores permanentes se rechazan sin reencolar; los temporales tienen hasta tres intentos antes de la DLQ. Un handler compartido mantiene el envío de ACK/NACK fuera del reintento del negocio. Consulta el [paso 14](docs/14-ack-manual-y-clasificacion-de-errores.md).

Rabbit Admin ofrece CRUD de colas, exchanges y bindings mediante una capa de servicio y solicitudes validadas. Sus endpoints requieren JWT con rol Admin y solo modifican recursos operativos con prefijo `andesstay.ops.`. Las actualizaciones usan políticas y el borrado exige recursos sin uso. El [paso 15](docs/15-rabbit-admin-crud.md) incluye rutas, ejemplos y motivos.

El dashboard permite consultar los consumidores y, con rol Admin, pausar y reanudar cada uno. La pausa es gradual y se restablece al reiniciar el backend. El [paso 16](docs/16-control-consumidores.md) explica los estados, los permisos y cómo observar mensajes en espera.

El [paso 17](docs/17-rendimiento-rabbitmq.md) compara el procesamiento con 1 o 3 consumidores y prefetch de 1 o 10. Usa mensajes sintéticos y colas temporales, registra tiempos y tasas con calentamiento y repeticiones, y verifica ACK, ausencia de efectos duplicados y limpieza. No crea reservas ni utiliza MySQL.

La API usa ahora `/api/v1`: el frontend llama a `/api/v1/bff` y el BFF reenvía el JWT a las rutas v1 del backend. Las rutas anteriores siguen disponibles como alias con los mismos permisos. Rabbit Admin y Kafka Admin también ofrecen v1. El [paso 18](docs/18-api-versionada.md) explica el contrato, la compatibilidad y cómo comprobarlo en local.

Nginx limita las solicitudes bajo `/api/` a 10 por segundo por IP, con margen para una ráfaga de 20 solicitudes adicionales. El exceso recibe HTTP 429 y `Retry-After: 3`; el cliente muestra el aviso y no repite automáticamente operaciones. Los alias comparten la cuota y el contenido estático queda fuera del límite. Consulta el [paso 19](docs/19-limites-api-local.md), sus pruebas y su alcance en el entorno Docker local.

El [paso 20](docs/20-cluster-rabbitmq-recuperacion.md) incorpora un laboratorio separado de tres nodos RabbitMQ con una cola quorum. Comprueba caída del líder, reconexión a otro nodo, reentrega después del commit sin duplicar avisos, pérdida de mayoría y recuperación. Usa el consumidor real de recepción y H2 con eventos sintéticos; la aplicación habitual conserva su broker local de un nodo.

El [paso 21](docs/21-evidencias-ep3.md) relaciona los ocho indicadores de EP3 con su implementación y evidencia, documenta los permisos y distingue los pendientes de registro real, base de datos cloud y entrega. [TESTS.md](TESTS.md) indexa los grupos de pruebas, su alcance real/simulado y los puntos de comprobación pendientes.

## Microsoft Entra

La plantilla conserva los identificadores públicos del proyecto; no contiene secretos de aplicación. Revisa en tu registro:

- Redirect URI de tipo SPA: `http://localhost:5173`.
- Permiso delegado de la API: el scope indicado en `ENTRA_SCOPE`.
- Roles `Admin`, `Recepcionista` y `Auditor`, asignados explícitamente al personal. Los clientes nuevos obtienen acceso de `Huesped` en el backend al completar su perfil. Las asignaciones existentes de `Huesped` siguen funcionando.
- `ENTRA_ISSUER_URI` y `ENTRA_AUDIENCE` deben coincidir con el token de acceso que emite tu API. Si utilizas tokens v2, configura los valores del registro v2; no mezcles su emisor y audiencia con los de v1.
- El frontend consulta los permisos efectivos de `/api/bff/account/profile`. El backend y el BFF validan el token de acceso y aplican los permisos. Si son registros separados, los roles del personal deben estar configurados en el registro de la API.

El 4 de octubre de 2026 se habilitó el registro público B2B en el directorio Cristobal y se creó el flujo `B2X_1_AndesStayPublico`, con Microsoft Entra ID, Cuenta Microsoft y código de un solo uso por correo, asociado exclusivamente a AndesStay. El cliente SPA quedó preautorizado solo para `access_as_user` de nuestra API.

El botón de `/register` inicia el alta de una identidad externa con `prompt=create`. La página permite completar un perfil después de autenticar la identidad mediante Microsoft Entra y habilita automáticamente el acceso de huésped para identidades delegadas sin roles asignados. El JWT continúa siendo emitido por Microsoft. La configuración de Entra está guardada; queda pendiente completar el alta con una cuenta externa real para verificar el recorrido completo. Consulta el [registro público, sus límites y la configuración aplicada](docs/09-registro-publico.md).

## Pruebas

Para ejecutar las pruebas sin levantar los brokers necesitas JDK 17 y Node 24 LTS, y haber instalado las dependencias del frontend con `npm ci` desde su carpeta. Maven se obtiene con los wrappers del proyecto.

```powershell
.\scripts\test-local.cmd
```

El script comprueba primero las políticas generadas de RabbitMQ, recompila los cuatro módulos Java desde cero, prueba el frontend, ejecuta ESLint y construye React. Las pruebas normales utilizan H2 y componentes de broker simulados.

Con el entorno Docker iniciado y una copia local en `backup/andesstay.sql`, prueba el motor y los brokers reales. Los respaldos de base de datos no se versionan; al clonar el repositorio debes proporcionar esa copia local si quieres ejecutar la verificación del respaldo:

```powershell
.\scripts\test-mysql.cmd
```

Crea tres esquemas temporales con prefijo `andesstay_test_`, importa el respaldo únicamente en uno de ellos, comprueba migraciones y conservación de los datos, ejecuta las 31 pruebas de disponibilidad sobre MySQL y verifica entrega y deduplicación con Kafka/RabbitMQ. También comprueba los tres intentos de entrega, la cuarentena y la caducidad. RabbitMQ usa exchanges, colas y políticas temporales con prefijo aleatorio, que se eliminan al terminar; el backend local continúa funcionando. Elimina sus esquemas temporales en `finally` y verifica que el archivo SQL original no haya cambiado.

Los resúmenes quedan en `.validation/`, excluida de Git. `mysql-migrations.json` contiene las huellas de los datos originales y los identificadores de solapamientos históricos.

Para probar el CRUD de Rabbit Admin con RabbitMQ local, sin importar MySQL:

```powershell
.\scripts\test-rabbit-admin.cmd
```

La prueba arranca una instancia aislada de Rabbit Admin con autenticación simulada únicamente dentro del test, usa el broker real y elimina sus recursos `andesstay.ops.verify.*`. Comprueba políticas, permisos, eliminación condicionada y entrega por las rutas creadas.

Para comparar rendimiento de consumidores con RabbitMQ local iniciado:

```powershell
.\scripts\test-rabbit-benchmark.cmd
```

Cada combinación procesa 300 mensajes en tres repeticiones, tras un calentamiento de 30 mensajes. El trabajo simulado solicita una espera de 5 ms por mensaje y registra también su duración observada. Los resultados compartibles quedan en `.ecc/benchmarks/rabbitmq-latest.json`; el log local queda en `.validation/`. Esta medición no incluye transacciones de MySQL ni llamadas HTTP y no cambia la configuración normal de avisos. Consulta el [procedimiento, los parámetros y los límites](docs/17-rendimiento-rabbitmq.md).

Para probar los límites del punto de entrada con Docker Desktop y Node 24:

```powershell
.\scripts\test-api-gateway.cmd
```

La prueba monta la configuración real de Nginx en un proyecto temporal con un BFF de prueba. Usa un puerto aleatorio de localhost, sin `.env`, JWT reales, reservas ni volúmenes de datos. Verifica el rechazo antes del BFF, la cuota compartida entre alias, las cabeceras, el contenido estático y la recuperación. Descarga las imágenes oficiales si faltan y elimina sus dos contenedores y su red al terminar. El resultado queda en `.validation/api-gateway-latest.json`.

Para probar la recuperación con tres nodos, Docker Desktop, Node 24 y JDK 17:

```powershell
.\scripts\test-rabbit-cluster.cmd
```

El script genera credenciales en memoria y usa puertos aleatorios de localhost.
No carga `.env` ni modifica MySQL o las colas reales. Provoca caídas exclusivamente
en su proyecto temporal y verifica sus etiquetas antes de controlarlo y limpiar
sus volúmenes. La evidencia compartible queda en
`.ecc/evidence/rabbitmq-cluster-latest.json`; el log local queda en `.validation/`.

Verificación del 5 de octubre de 2026:

| Módulo o entorno | Pruebas ejecutadas y aprobadas |
| --- | ---: |
| Backend, entorno normal | 187 |
| BFF | 61 |
| Kafka Admin y Rabbit Admin | 2 |
| Frontend | 38 |
| MySQL y brokers reales | 34 |

Las tres pruebas específicas del entorno externo se omiten en la ejecución normal del backend y se ejecutan con `test-mysql.cmd`. Las 31 pruebas de disponibilidad se ejecutan tanto con H2 como con MySQL. ESLint, la construcción del frontend y el arranque Docker también pasaron.

Verificación de la centralización del 8 de octubre de 2026: backend 189 pruebas aprobadas en ejecución normal, BFF 61, Kafka Admin 1, Rabbit Admin 2 y frontend 38; ESLint y build correctos. Pasaron además tres escenarios de generación de políticas y el empaquetado del catálogo compartido en ambos JAR. Las 34 pruebas con MySQL/brokers reales también pasaron, incluidas las tres pruebas externas omitidas en la ejecución normal. Se reconstruyeron las imágenes de backend y Rabbit Admin y quedaron siete servicios saludables. El [paso 12](docs/12-topologia-rabbitmq-centralizada.md) registra el detalle y el ajuste de sincronización de la prueba de políticas.

Verificación de la organización de consumidores del 8 de octubre de 2026: volvieron a pasar las 189 pruebas normales del backend y las 34 de MySQL/brokers reales. El backend actualizado contiene las tres clases nuevas y elimina la anterior; los siete servicios están saludables, cada cola de trabajo tiene un consumidor y las DLQ no tienen ninguno. El [paso 13](docs/13-consumidores-por-dominio.md) registra los cambios, sus motivos y las evidencias.

Verificación del ACK manual del 8 de octubre de 2026: 209 pruebas normales del backend y 34 de MySQL/brokers reales aprobadas. Se comprobó ACK después del commit, clasificación de errores, NACK sin reencolado y reentrega real tras perder el canal sin duplicar el aviso. El backend actualizado está saludable junto con los otros seis servicios y no hay mensajes pendientes de ACK. El [paso 14](docs/14-ack-manual-y-clasificacion-de-errores.md) registra el detalle.

Verificación del CRUD de Rabbit Admin del 8 de octubre de 2026: 51 pruebas normales aprobadas y un recorrido completo con RabbitMQ real. Pasaron también las 2 pruebas del contrato de topología y los 3 escenarios del generador de políticas. Se verificaron permisos, validación, borrado condicionado, TTL/límite, cambio de routing key, DLX y exchange alternativo. Backend y Rabbit Admin se reconstruyeron con el catálogo actual; los 7 servicios están saludables y los recursos temporales se eliminaron. El [paso 15](docs/15-rabbit-admin-crud.md) contiene los endpoints y ejemplos.

Verificación del control de consumidores del 9 de octubre de 2026: 234 pruebas normales del backend, 69 del BFF y 45 del frontend aprobadas; ESLint y construcción correctos. Una prueba adicional con RabbitMQ real comprobó pausa gradual, ACK, mensajes en espera, entrega independiente de huéspedes y reanudación. Las dos colas temporales se eliminaron. Ejecuta `scripts/test-listener-control.cmd` con RabbitMQ iniciado para repetirla. El [paso 16](docs/16-control-consumidores.md) explica cómo probarlo desde el dashboard.

Verificación del rendimiento del 9 de octubre de 2026: una prueba real aprobada con 12 mediciones, 3600 mensajes medidos y 120 de calentamiento, sin mensajes faltantes ni efectos duplicados. Las medianas fueron de unos 64 msg/s con un consumidor y 193 msg/s con tres; el cambio de prefetch produjo poca diferencia en esta carga sintética. Pasaron también las 234 pruebas normales del backend, con cinco externas omitidas. No quedaron colas temporales y los seis servicios locales siguen saludables. El [paso 17](docs/17-rendimiento-rabbitmq.md) contiene la tabla, las condiciones de medición y los límites de interpretación.

Verificación del versionado del 13 de octubre de 2026: 245 pruebas del backend, 78 del BFF, 53 de Rabbit Admin, 9 de Kafka Admin y 45 del frontend aprobadas; seis pruebas externas omitidas. ESLint y construcción correctos. Se verificaron los alias, JWT, roles y cabeceras `Location`; las imágenes locales se actualizaron y los seis servicios principales están saludables. En HTTP real, la web respondió 200 y ambas rutas de reservas rechazaron con 401 las peticiones sin JWT. El [paso 18](docs/18-api-versionada.md) documenta también la espera de la API RabbitMQ durante la importación inicial.

Verificación de los límites del 13 de octubre de 2026: 52 pruebas del frontend,
ESLint y construcción correctos. Pasaron seis escenarios aislados con Nginx real:
79 de 100 solicitudes de una ráfaga recibieron 429 y no llegaron al BFF de prueba.
Después de esperar, volvió a aceptar solicitudes. El frontend local se actualizó,
los seis servicios quedaron saludables y la API conservó el rechazo de JWT
ausentes o inválidos. El [paso 19](docs/19-limites-api-local.md) registra el alcance
y cómo repetir la prueba.

Verificación del cluster del 13 de octubre de 2026: una prueba real aprobada con
tres réplicas, caída del líder, reconexión, reentrega y recuperación tras perder
mayoría. Quedaron 21 eventos únicos, 21 avisos y 21 registros inbox. Sus recursos
temporales se eliminaron. La suite normal del backend pasó con 245 pruebas y
seis externas omitidas; también pasaron los seis escenarios del gateway. Los
seis servicios habituales siguen saludables. El [paso 20](docs/20-cluster-rabbitmq-recuperacion.md)
explica el alcance y la evidencia compartible.

## Base de datos existente

Flyway administra ahora los cambios de estructura; Hibernate usa `validate`. El arranque normal crea un esquema nuevo sin importar el respaldo. Para una instalación anterior, primero respalda y verifica una copia con el procedimiento de [migraciones](docs/04-entorno-y-migraciones.md). `DB_BASELINE_EXISTING=true` es una opción explícita para reconocer el esquema anterior como versión 1; no debe activarse por defecto.

Se detectaron conflictos históricos entre las reservas 4, 5 y 6 de la unidad 2 en el respaldo. No se cambió su estado ni sus fechas. Recepción debe decidir cómo resolverlos antes de utilizar ese historial operativo.

## Cambios y motivos

1. [Permisos y propiedad de reservas](docs/01-permisos-reservas.md).
2. [Disponibilidad, fechas y bloqueos](docs/02-disponibilidad-reservas.md).
3. [Filtros de auditoría](docs/03-filtros-auditoria.md).
4. [Entorno, secretos y migraciones MySQL](docs/04-entorno-y-migraciones.md).
5. [Entrega fiable de eventos](docs/05-eventos-confiables.md).
6. [Catálogo, reportes y frontend](docs/06-catalogo-reportes-interfaz.md).
7. [Diseño del frontend: colores, navegación y adaptación a móvil](docs/07-diseno-frontend.md).
8. [Registro de perfil con Microsoft, JWT y permisos](docs/08-registro-con-microsoft.md).
9. [Registro público y acceso automático de huésped](docs/09-registro-publico.md).
10. [RabbitMQ: exchange, bindings y avisos de reservas](docs/10-rabbitmq-reservas.md).
11. [RabbitMQ: DLX, DLQ, retención y alertas](docs/11-rabbitmq-dlq-retencion.md).
12. [RabbitMQ: nombres, rutas y políticas centralizados](docs/12-topologia-rabbitmq-centralizada.md).
13. [RabbitMQ: consumidores organizados por dominio](docs/13-consumidores-por-dominio.md).
14. [RabbitMQ: ACK manual y clasificación de errores](docs/14-ack-manual-y-clasificacion-de-errores.md).
15. [Rabbit Admin: CRUD, servicio, validación y pruebas reales](docs/15-rabbit-admin-crud.md).
16. [RabbitMQ: control de consumidores desde la API y el dashboard](docs/16-control-consumidores.md).
17. [RabbitMQ: comparación de rendimiento con consumidores, prefetch y pausas](docs/17-rendimiento-rabbitmq.md).
18. [API versionada: rutas v1, JWT y compatibilidad con las rutas anteriores](docs/18-api-versionada.md).
19. [Límites de solicitudes en la API local y respuesta HTTP 429](docs/19-limites-api-local.md).
20. [RabbitMQ: cluster quorum, caída del líder, reconexión y deduplicación](docs/20-cluster-rabbitmq-recuperacion.md).
21. [EP3: matriz de evidencias, permisos y pendientes de entrega](docs/21-evidencias-ep3.md).

## Servicios de operación opcionales

Antes de usar Compose directamente, ejecuta `.\scripts\export-rabbit-topology.cmd`. Los administradores se levantan con `docker compose --env-file .env -f docker/docker-compose.yml --profile operations up --build -d --wait`. Kafka Admin usa localhost:8092 y Rabbit Admin localhost:8091. Sus API requieren el rol Admin.

Este entorno tiene un nodo Kafka y un servidor MySQL/RabbitMQ para desarrollo. La redundancia, las copias programadas, la retención de outbox/inbox y la configuración del despliegue deben definirse según el entorno de operación.

## Archivos privados

Git excluye `.env`, las claves privadas, los respaldos SQL, las compilaciones, las dependencias instaladas y las capturas del directorio Entra o de cuentas reales. `.env.example` contiene la plantilla de configuración y `scripts/setup-local.cmd` prepara el entorno local.
