# Paso 18: versionar las rutas de la API

## Qué cambia y para qué

La API actual se identifica con `/api/v1`. El frontend llama al BFF usando ese
prefijo y el BFF llama a las rutas v1 del backend. Los servicios de operación
Rabbit Admin y Kafka Admin también ofrecen sus rutas v1.

Por ejemplo, la página de reservas consulta ahora
`GET /api/v1/bff/reservations`. El BFF valida su JWT y lo reenvía a
`GET /api/v1/reservations` del backend. La respuesta conserva su estructura de
paginación, estados HTTP y reglas de acceso a las reservas.

La versión identifica un contrato entre cliente y servidor. Si más adelante
se cambia de forma incompatible el contenido de una respuesta, una futura v2
podría convivir con v1 mientras se actualizan sus clientes. Agregar un campo
opcional o corregir un fallo no obliga por sí solo a crear otra versión.

Este paso se ejecuta en local y no reactiva la preparación de AWS. Aplica el
paso de versionado previsto en [el orden de trabajo](16-control-consumidores.md).
Los límites de solicitudes y la recuperación del broker son etapas siguientes.

## Rutas principales

| Servicio | Prefijo v1 | Ejemplo |
|---|---|---|
| BFF, acceso desde el navegador | `/api/v1/bff` | `/api/v1/bff/reservations` |
| Backend: reservas | `/api/v1/reservations` | `/api/v1/reservations/1/status` |
| Backend: catálogo | `/api/v1/catalog/units` | `/api/v1/catalog/units/1` |
| Backend: cuenta y registro | `/api/v1/account` | `/api/v1/account/profile` |
| Backend: reportes | `/api/v1/reports` | `/api/v1/reports/operations` |
| Backend: auditoría | `/api/v1/audit` | `/api/v1/audit?page=0&size=20` |
| Backend: avisos | `/api/v1/notifications` | `/api/v1/notifications` |
| Backend: mensajería | `/api/v1/messaging` | `/api/v1/messaging/listeners` |
| Rabbit Admin | `/api/v1/rabbitmq` | `/api/v1/rabbitmq/queues` |
| Kafka Admin | `/api/v1/kafka` | `/api/v1/kafka/topics` |

Los puertos y la forma de iniciar la aplicación siguen siendo los del entorno
local. Las comprobaciones de salud continúan en `/actuator/health` y sus subrutas;
son endpoints técnicos de Actuator y no se versionan junto con la API de negocio.

## Compatibilidad y seguridad

Cada controlador admite explícitamente su ruta v1 y la ruta anterior mediante
un arreglo de rutas en la misma anotación. Ambas ejecutan el mismo método, con
los mismos servicios, validaciones y reglas de propiedad de las reservas.
Spring admite este arreglo en `@RequestMapping` y `@GetMapping`:
[documentación de rutas](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html).

Las reglas de seguridad del backend y BFF incluyen ambos prefijos. Se conserva
el orden de las reglas por método y recurso, los roles y el rechazo por defecto.
Versionar una URL sin actualizar estos filtros impediría el acceso a usuarios
válidos. Véase la [autorización HTTP de Spring Security](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html).

- Sin JWT o con un JWT inválido, ambas rutas requieren autenticación.
- Huésped conserva el acceso a sus reservas, catálogo y avisos; no obtiene
  permisos de administración por cambiar de prefijo.
- La pausa y reanudación de consumidores continúan restringidas a Admin.
- Las identidades públicas registradas conservan su validación de perfil antes
  de recibir permisos de Huésped.
- Rabbit Admin y Kafka Admin conservan su requisito del rol Admin.
- No se usa un patrón que admita cualquier número de versión. Las versiones no
  implementadas no llegan al servicio de negocio.

La ruta antigua es un alias de compatibilidad, no una versión con código
duplicado. El frontend y el BFF ya usan v1. No se ha acordado una fecha para retirar
los alias; las pruebas los mantienen cubiertos mientras existan.

Rabbit Admin devuelve ahora cabeceras `Location` con rutas v1 al crear una cola,
exchange o binding, incluso cuando la creación se solicitó por el alias antiguo.
La dirección se puede consultar con el mismo JWT y los mismos permisos.

## Cómo comprobarlo

Desde la raíz del repositorio:

```powershell
.\scripts\start-local.cmd
```

Después, abre `http://localhost:5173`, inicia sesión con Microsoft y consulta
catálogo, reservas y dashboard. En las herramientas de desarrollo del navegador,
la pestaña Network muestra las peticiones bajo `/api/v1/bff/`.

Sin iniciar sesión, se puede comprobar que las dos rutas requieren JWT:

```powershell
curl.exe -i http://localhost:5173/api/v1/bff/reservations
curl.exe -i http://localhost:5173/api/bff/reservations
```

Ambas deben devolver HTTP 401. No pegues tokens reales en archivos compartidos,
capturas o comandos que vayas a publicar.

Las pruebas de backend y BFF cubren permisos por rol, validaciones, errores,
registro de perfil y control de consumidores mediante v1. Las pruebas adicionales
comparan la ruta anterior y v1, comprueban el JWT reenviado al backend y rechazan
versiones no configuradas. El frontend comprueba sus nuevas URL y conserva las
pruebas de interacción. Rabbit Admin verifica su CRUD, sus `Location` y el alias;
Kafka Admin comprueba ambos prefijos y sus permisos.

El respaldo anterior está en `backup/code-step-20-api-versioning/`, excluido de
Git. Los resultados locales se guardan en `.validation/step-20-*.log`.

## Verificación del 13 de octubre de 2026

| Componente | Pruebas aprobadas | Pruebas externas omitidas |
|---|---:|---:|
| Backend | 245 | 5 |
| BFF | 78 | 0 |
| Rabbit Admin | 53 | 1 |
| Kafka Admin | 9 | 0 |
| Frontend | 45 | 0 |

No se registraron fallos ni errores. ESLint y la construcción del frontend
también pasaron. La suite completa del backend se ejecutó primero; después se
amplió y verificó su clase de compatibilidad con dos casos de identidad de cuenta.
Las cifras agregan los informes JUnit vigentes de las clases ejecutadas.
Las seis pruebas externas requieren activar explícitamente sus respectivos
entornos de MySQL/brokers y no forman parte de esta ejecución normal.

Se comprobó que el frontend solo cambia sus URL, que el BFF reenvía el JWT a v1
y que las reglas de roles, CORS y rechazo por defecto se conservan. La prueba de
cuenta verifica que ambas rutas devuelven `tid` y `oid` del JWT verificado, junto
con la zona horaria, usando la clase real que obtiene la identidad.

Se reconstruyeron las cinco imágenes de aplicación. Los seis servicios locales
principales quedaron saludables y `rabbit-setup` terminó correctamente después
de reiniciar el mismo contenedor del broker. No se eliminaron volúmenes.

La comprobación HTTP real verificó login HTTP 200, reservas por v1 y por el alias
HTTP 401 sin JWT, perfil v1 HTTP 401 sin JWT y rechazo HTTP 401 de un token inválido.
Los archivos JavaScript servidos por Nginx contienen las rutas `/api/v1/bff`.
El resumen queda en `.validation/step-20-verification.json`. El recorrido visual
con una sesión real de Microsoft puede comprobarse con los pasos indicados arriba.

Durante el arranque se detectó que `rabbit-setup` podía importar las definiciones
antes de que RabbitMQ aceptara solicitudes HTTP. Su comando ahora espera una
respuesta autenticada de la API, con hasta 30 intentos, peticiones de 3 segundos
y pausas de 2 segundos. Si no responde, termina con error y el backend no se inicia
con una topología incompleta. La espera se aplica al importador; conserva el
contenedor y el volumen del broker.

Docker Desktop también falló al iniciar por sockets temporales inaccesibles en
Windows. Se recuperó conservando y regenerando sus carpetas temporales, sin
restablecer Docker ni modificar sus discos de datos. El mismo síntoma y la
recuperación mediante renombrado de carpetas están descritos en
[el reporte del repositorio de Docker](https://github.com/docker/desktop-feedback/issues/554).
