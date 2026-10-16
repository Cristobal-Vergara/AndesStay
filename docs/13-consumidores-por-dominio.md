# Paso 13: consumidores RabbitMQ organizados por dominio

Segunda etapa de adaptación a la EP3. Se separan los consumidores de avisos y de demostración sin cambiar el contenido de los eventos ni los nombres de las colas.

## 1. Qué problema había

`ReservationConsumer` escuchaba las dos colas de avisos en la misma clase. Además, `MessagingDemoService` publicaba la prueba y también consumía el mensaje que debía fallar.

Los avisos de recepción y huéspedes tienen destinatarios y reglas diferentes. Tener una clase por consumidor permite localizar su entrada, identificar su contenedor y modificar su manejo de errores sin mezclar ambas entregas. Agrupar esas clases en el dominio de notificaciones muestra para qué sirven.

## 2. Cómo quedan las carpetas

Dentro de `AndesStay Backend/src/main/java/com/example/AndesStay/`:

```text
messaging/
├── notifications/
│   ├── ReceptionNotificationConsumer.java
│   └── GuestNotificationConsumer.java
└── demo/
    └── DeliveryDemoConsumer.java
config/
├── RabbitMQConfig.java
└── RabbitDemoConfig.java
service/
├── InboxService.java
├── ReservationNotificationService.java
└── MessagingDemoService.java
```

Los consumidores son las entradas desde RabbitMQ. La configuración declara colas, exchanges y bindings. Los servicios mantienen la transacción, la deduplicación y las reglas de los avisos. La clase anterior `ReservationConsumer` se elimina para evitar registrar también sus listeners.

| Consumidor | Cola obtenida del bean | Eventos que llegan por los bindings | Efecto |
| --- | --- | --- | --- |
| `ReceptionNotificationConsumer` | `reservationQueue` | Creación, confirmación y cancelación | Aviso de recepción |
| `GuestNotificationConsumer` | `guestNotificationQueue` | Confirmación y cancelación | Aviso del huésped asignado |
| `DeliveryDemoConsumer` | `demoQueue` | Prueba aislada | Falla de forma controlada para demostrar reintentos y DLQ |

Las anotaciones `@RabbitListener` siguen utilizando los beans Queue. Los nombres físicos y routing keys continúan procediendo del catálogo compartido del paso 12.

Cada listener tiene un identificador de contenedor estable: `receptionNotifications`, `guestNotifications` y `deliveryDemo`. Sirven para identificar los listeners dentro de Spring; no son nombres de recursos del broker.

## 3. Qué ocurre al llegar una confirmación

1. El exchange entrega una copia a recepción y otra a huéspedes, mediante sus bindings.
2. Cada consumidor convierte el cuerpo del mensaje de UTF-8 a texto.
3. Llama a `InboxService.process` con su destino y el evento.
4. Si esa entrega ya fue procesada, el inbox evita repetir el aviso.
5. Si es nueva, `ReservationNotificationService` valida el evento y guarda el aviso correspondiente dentro de la misma transacción.
6. En esta etapa Spring confirmaba la entrega con el modo AUTO. Desde el [paso 14](14-ack-manual-y-clasificacion-de-errores.md), el handler envía ACK explícito después del commit.

La separación mantiene la privacidad: el servicio obtiene el destinatario del evento y restringe la consulta por tenant y usuario. La cola del huésped no recibe la creación; tampoco genera avisos para una reserva creada por personal sin huésped asignado.

## 4. Por qué se conserva una deduplicación por audiencia

Los destinos del inbox siguen siendo `RABBITMQ` para recepción y `RABBITMQ_GUEST` para huéspedes. Son claves de datos existentes, no los nombres físicos de las colas.

La clave de una entrega incluye destino e identificador del evento. Una misma confirmación debe producir dos avisos, uno por audiencia. Si ambas clases utilizaran el mismo destino, la segunda copia se descartaría como duplicada. Con destinos distintos, las dos audiencias reciben su aviso y los reintentos de cada una se deduplican de forma independiente.

La escritura del aviso y del recibo inbox sigue siendo atómica: un error revierte ambas. En esta etapa las excepciones se propagaban al contenedor; desde el [paso 14](14-ack-manual-y-clasificacion-de-errores.md), el handler compartido aplica reintentos, clasificación y NACK explícito.

## 5. Cómo queda la demostración

`MessagingDemoService` conserva la publicación persistente y la comprobación de confirmación del broker. `DeliveryDemoConsumer` contiene únicamente el fallo controlado.

`RabbitDemoConfig` registra el consumidor junto con los recursos de demostración solo cuando `andesstay.messaging.demo-enabled=true`. No se activa por el simple escaneo de clases. La demostración utiliza su propia cola y DLQ y no escribe reservas ni avisos.

## 6. Qué aporta a la rúbrica y qué queda

Esta etapa organiza los `@RabbitListener` por dominio funcional y separa las entradas de mensajería de los servicios de negocio y de la configuración.

Los consumidores siguen ejecutándose dentro del backend. Organizar paquetes prepara la separación, pero todavía no crea un microservicio independiente de notificaciones.

El [paso 14](14-ack-manual-y-clasificacion-de-errores.md) agrega ACK explícito y clasificación de errores sobre estas clases separadas. El [paso 15](15-rabbit-admin-crud.md) incorpora el CRUD de Rabbit Admin con validación y servicio propio. Siguen pendientes el servicio independiente y el despliegue de la base de datos en la nube.

## 7. Cómo comprobarlo

Desde la raíz del repositorio:

```powershell
.\scripts\test-local.cmd
.\scripts\test-mysql.cmd
.\scripts\start-local.cmd
```

La primera orden ejecuta la suite local. La segunda necesita Docker iniciado y la copia local `backup/andesstay.sql`; utiliza esquemas y recursos de broker temporales. La tercera construye y arranca la aplicación.

Para ejecutar únicamente las pruebas normales del backend:

```powershell
Set-Location '.\AndesStay Backend'
.\mvnw.cmd clean test
```

`ReservationNotificationIntegrationTests` utiliza ahora las dos clases independientes y comprueba avisos de acciones reales, deduplicación, privacidad, reversión transaccional y compatibilidad con eventos anteriores.

`RealBrokerIntegrationTests` verifica que las colas de recepción, huéspedes y demostración tengan exactamente un consumidor y que las DLQ no tengan ninguno. Después comprueba el recorrido real de reservas, reintentos, cuarentena, caducidad y retención. La verificación de tres intentos de la demo observa ahora `DeliveryDemoConsumer`.

## 8. Evidencias de esta etapa

Verificación del 8 de octubre de 2026:

| Comprobación | Resultado |
| --- | --- |
| Backend, ejecución normal desde una compilación limpia | 189 aprobadas; 3 externas omitidas en esta ejecución |
| MySQL, RabbitMQ y Kafka reales | 34 aprobadas, incluidas las 3 externas |
| Compilación y JAR instalado | Contienen las tres clases nuevas y no contienen `ReservationConsumer` |
| Imagen y contenedor del backend | Reconstruidos y arrancados correctamente |
| Servicios Docker | 7 saludables |
| Recepción, huéspedes y demostración | 1 consumidor por cola, 0 mensajes pendientes |
| DLQ real y DLQ de demostración | 0 consumidores y 0 mensajes |
| Recursos temporales | Esquemas eliminados; sin colas ni políticas temporales; respaldo SQL sin cambios |
| HTTP local | Dashboard 200; estado de mensajería BFF y Rabbit Admin 401 sin JWT |

Las pruebas de Java y las operaciones Docker se ejecutaron fuera del entorno restringido del agente, como en el paso anterior. Los registros de este paso están en `.validation/step-15-backend-tests.log`, `.validation/step-15-real-tests.log`, `.validation/mysql-tests.log`, `.validation/step-15-docker-build.log` y `.validation/step-15-docker-start.log`.

El respaldo local anterior a esta etapa está en `backup/code-step-15-consumers/`, excluido de Git.
