# Paso 14: ACK manual, errores y reintentos de RabbitMQ

Tercera etapa de adaptación a la EP3. Recepción, huéspedes y demostración ahora controlan explícitamente la confirmación o el rechazo de cada entrega.

## 1. Qué problema resuelve

Spring ya confirmaba las entregas exitosas con su modo AUTO. La rúbrica pide que el consumidor haga explícita la decisión y diferencie errores temporales y permanentes.

El cambio debe conservar el orden correcto: terminar la transacción de MySQL antes de confirmar en RabbitMQ. Si se enviara ACK antes del commit, un error de base de datos podría dejar una entrega confirmada sin su aviso.

El modo AUTO anterior de Spring confirmaba al terminar el listener; no debe confundirse con el `autoAck` del protocolo RabbitMQ, que confirma al entregar al cliente.

## 2. Qué hace ahora cada consumidor

Cada `@RabbitListener` declara `ackMode = "MANUAL"` y recibe el `Channel` que corresponde a su entrega. Delega en `RabbitDeliveryHandler` el manejo compartido de reintentos, ACK y NACK.

| Resultado | Decisión |
| --- | --- |
| Efecto guardado y commit terminado | `basicAck(tag, false)` |
| Evento ya procesado por esa audiencia | ACK, sin repetir el efecto |
| Mensaje inválido o fallo permanente de datos | `basicNack(tag, false, false)`, sin reintentos |
| Fallo temporal recuperado | ACK después del intento exitoso |
| Fallo temporal o no clasificado que agota tres intentos | NACK sin reencolar; RabbitMQ aplica el DLX/DLQ |
| Interrupción durante el procesamiento o la espera | NACK con reencolado y conservación de la interrupción |
| Fallo al enviar ACK/NACK | Se cierra el canal y se propaga el error |
| Error grave de Java | Se cierra el canal y se propaga, sin ACK/NACK |

El argumento `false` de `multiple` limita la decisión al mensaje actual. El identificador de entrega se obtiene del mensaje recibido y se utiliza en su mismo canal.

La [documentación de confirmaciones de RabbitMQ](https://www.rabbitmq.com/docs/confirms) explica los identificadores de entrega, los rechazos y la recuperación de entregas sin confirmar cuando se cierra el canal.

## 3. Cómo se clasifican los errores

`InvalidNotificationEventException` expresa un error permanente del aviso. El servicio la utiliza para JSON mal formado o que no es un objeto, identificadores inválidos, identidad de huésped inválida y fecha ilegible.

Se conserva la compatibilidad anterior: el texto plano del productor original y los sobres anteriores sin un tipo de reserva compatible se registran en inbox sin inventar avisos.

`RabbitDeliveryFailureClassifier` inspecciona también las causas de excepciones envueltas:

- Un evento inválido no se reintenta.
- Un error no transitorio de acceso a datos, como una restricción incumplida o una consulta inválida, no se reintenta.
- La falta temporal de un recurso o conexión se puede reintentar. `DataAccessResourceFailureException` se trata expresamente como recuperable aunque hereda de una categoría no transitoria de Spring.
- Otros errores de procesamiento tienen el mismo presupuesto finito de reintentos y, si persisten, se rechazan.
- Una interrupción cancela los reintentos y permite recuperar la entrega.

El recorrido de causas evita ciclos. Los errores graves de Java no se repiten.

Los logs registran cola, reencolado, cantidad de reintentos y clase del error. No imprimen el contenido del mensaje, la identidad del huésped ni la descripción completa de la excepción.

## 4. Por qué cambiamos el lugar de los reintentos

`RabbitDeliveryConfig` construye la política y el handler compartido. Se utiliza el `RetryTemplate` de Spring Framework 7, ya incluido por las dependencias del proyecto; [su API oficial](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/retry/RetryTemplate.html) documenta la ejecución y los resultados de reintento.

La configuración sigue en `src/main/resources/rabbit-delivery.properties`, importada también por las pruebas:

```properties
spring.rabbitmq.listener.simple.acknowledge-mode=manual
spring.rabbitmq.listener.simple.prefetch=1
spring.rabbitmq.listener.simple.default-requeue-rejected=false
spring.rabbitmq.listener.simple.retry.enabled=false
andesstay.rabbitmq.delivery.max-retries=2
andesstay.rabbitmq.delivery.initial-interval-ms=1000
andesstay.rabbitmq.delivery.multiplier=2
andesstay.rabbitmq.delivery.max-interval-ms=10000
```

Dos reintentos significan tres intentos totales, con esperas de 1 y 2 segundos. Se desactiva el reintento automático del contenedor para tener una sola política y evitar repetir el negocio por un fallo del ACK.

El callback de reintento contiene únicamente el procesamiento. ACK y NACK quedan fuera. El recoverer anterior se sustituye por el rechazo explícito del handler.

## 5. Cómo se conserva la transacción

El listener no inicia una transacción que abarque el ACK. Cada intento llama a `InboxService.process` a través del bean de Spring, que inicia su transacción y registra el recibo de entrega.

`ReservationNotificationService.receive` participa en esa misma transacción. Si falla el aviso o el commit, el recibo inbox también se revierte. Un nuevo intento comienza una nueva transacción.

Cuando `InboxService.process` regresa, el commit ya terminó. Solo entonces el handler confirma la entrega. Recepción sigue deduplicando con `RABBITMQ` y huéspedes con `RABBITMQ_GUEST`, conservando sus datos y sus efectos independientes.

Si se guarda el aviso y se pierde el canal antes del ACK, RabbitMQ puede entregar otra vez el evento. El recibo inbox ya confirmado evita guardar un segundo aviso; la nueva entrega se confirma.

## 6. Cómo queda la demostración

`DeliveryDemoConsumer` recibe una entrega y su callback `simulateFailure` falla tres veces antes del NACK. Su DLQ sigue separada de la real y conserva su retención. La demo continúa habilitada solo por configuración y no modifica reservas ni avisos.

No se reencolan indefinidamente los errores de negocio: se rechazan al ser permanentes o al agotar el presupuesto. El reencolado por interrupción corresponde a recuperar una entrega durante la detención del procesamiento.

## 7. Pruebas que lo demuestran

- `RabbitDeliveryHandlerTests`: ACK después del callback, entrega individual, rechazo inmediato de permanentes y sus causas, recuperación temporal en el tercer intento, agotamiento, fallos de ACK/NACK, interrupción y errores graves.
- `ReservationNotificationIntegrationTests`: aviso e inbox confirmados antes del ACK, redelivery sin duplicados después de un ACK fallido, rechazo de datos inválidos sin dejar escrituras, privacidad y compatibilidad.
- `RealBrokerIntegrationTests`: modo MANUAL en los tres listeners, rechazo de recepción y huéspedes con cabecera `x-death`, temporal recuperado y agotado, reversión del inbox, fallo del ACK tras commit con cierre de un canal real y nueva entrega marcada como redelivered, demo y retención.

Desde la raíz:

```powershell
.\scripts\test-local.cmd
.\scripts\test-mysql.cmd
.\scripts\start-local.cmd
```

Para ejecutar únicamente la suite normal del backend:

```powershell
Set-Location '.\AndesStay Backend'
.\mvnw.cmd clean test
```

Las pruebas externas usan esquemas y colas temporales; necesitan Docker y la copia local `backup/andesstay.sql`.

## 8. Evidencias y alcance

Verificación del 8 de octubre de 2026:

| Comprobación | Resultado |
| --- | --- |
| Backend desde compilación limpia | 209 aprobadas; 3 pruebas externas omitidas en esta ejecución |
| MySQL, RabbitMQ y Kafka reales | 34 aprobadas, incluidas las 3 externas |
| Pruebas específicas del handler | 14 aprobadas |
| Pruebas de avisos, transacciones y privacidad | 15 aprobadas |
| Modo de los listeners reales | MANUAL en recepción, huéspedes y demostración |
| Pérdida de canal después del commit | Reentrega real marcada como redelivered, un solo aviso y un solo recibo inbox |
| Imagen y backend local | Reconstruidos y arrancados; 7 servicios saludables |
| JAR instalado | Clases presentes y configuración idéntica a la fuente; ACK manual y reintento del contenedor desactivado |
| Colas de trabajo | 1 consumidor por cola; 0 mensajes listos y 0 sin confirmar |
| DLQ real y demo | 0 consumidores; 0 mensajes |
| Limpieza | Esquemas temporales eliminados; sin colas ni políticas temporales; respaldo SQL sin cambios |
| HTTP | Dashboard 200; estado de mensajería BFF y Rabbit Admin 401 sin JWT |

Los registros están en `.validation/step-16-backend-tests.log`, `.validation/step-16-real-tests.log`, `.validation/mysql-tests.log`, `.validation/step-16-docker-build.log` y `.validation/step-16-docker-start.log`. Java y Docker se ejecutaron fuera del entorno restringido del agente para permitir la instrumentación de Mockito y el acceso al motor local.

La primera ejecución de la suite detectó que `RetryTemplate` envuelve también un `Error` no reintentable. Se corrigió el handler para cerrar el canal y propagar ese error, conservando la prueba.

Esta etapa cubre el control explícito de entrega y la clasificación de errores. El [paso 15](15-rabbit-admin-crud.md) incorpora el CRUD de Rabbit Admin con su servicio y validación. Siguen pendientes el microservicio independiente de notificaciones y la base de datos en la nube.

El respaldo previo está en `backup/code-step-16-manual-ack/`, excluido de Git.
