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

El script recompila los cuatro módulos Java desde cero, prueba el frontend, ejecuta ESLint y construye React. Las pruebas normales utilizan H2 y componentes de broker simulados.

Con el entorno Docker iniciado y una copia local en `backup/andesstay.sql`, prueba el motor y los brokers reales. Los respaldos de base de datos no se versionan; al clonar el repositorio debes proporcionar esa copia local si quieres ejecutar la verificación del respaldo:

```powershell
.\scripts\test-mysql.cmd
```

Crea tres esquemas temporales con prefijo `andesstay_test_`, importa el respaldo únicamente en uno de ellos, comprueba migraciones y conservación de los datos, ejecuta las 31 pruebas de disponibilidad sobre MySQL y verifica entrega y deduplicación con Kafka/RabbitMQ. También comprueba los tres intentos de entrega, la cuarentena y la caducidad. RabbitMQ usa exchanges, colas y políticas temporales con prefijo aleatorio, que se eliminan al terminar; el backend local continúa funcionando. Elimina sus esquemas temporales en `finally` y verifica que el archivo SQL original no haya cambiado.

Los resúmenes quedan en `.validation/`, excluida de Git. `mysql-migrations.json` contiene las huellas de los datos originales y los identificadores de solapamientos históricos.

Verificación del 5 de octubre de 2026:

| Módulo o entorno | Pruebas ejecutadas y aprobadas |
| --- | ---: |
| Backend, entorno normal | 187 |
| BFF | 61 |
| Kafka Admin y Rabbit Admin | 2 |
| Frontend | 38 |
| MySQL y brokers reales | 34 |

Las tres pruebas específicas del entorno externo se omiten en la ejecución normal del backend y se ejecutan con `test-mysql.cmd`. Las 31 pruebas de disponibilidad se ejecutan tanto con H2 como con MySQL. ESLint, la construcción del frontend y el arranque Docker también pasaron.

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

## Servicios de operación opcionales

Se levantan con `docker compose --env-file .env -f docker/docker-compose.yml --profile operations up --build -d --wait`. Kafka Admin usa localhost:8092 y Rabbit Admin localhost:8091. Sus API requieren el rol Admin.

Este entorno tiene un nodo Kafka y un servidor MySQL/RabbitMQ para desarrollo. La redundancia, las copias programadas, la retención de outbox/inbox y la configuración del despliegue deben definirse según el entorno de operación.

## Archivos privados

Git excluye `.env`, las claves privadas, los respaldos SQL, las compilaciones, las dependencias instaladas y las capturas del directorio Entra o de cuentas reales. `.env.example` contiene la plantilla de configuración y `scripts/setup-local.cmd` prepara el entorno local.
