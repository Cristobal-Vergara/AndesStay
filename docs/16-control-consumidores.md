# Paso 16: pausar y reanudar consumidores RabbitMQ

## Qué incorporamos y para qué sirve

El dashboard tiene un apartado **Control de avisos**. Permite consultar cada consumidor y, con una cuenta Admin, pausar o reanudar su entrega. Recepción tiene acceso de consulta; huéspedes y auditores no tienen acceso a estos endpoints.

Este paso aplica el control de listeners estudiado en la guía 2.3.1 a nuestros avisos de reservas. Sirve para observar cómo se acumulan mensajes durante una pausa y cómo se procesan después. Es la base para la siguiente comparación de rendimiento.

## Por qué se implementa así

Los consumidores actuales se ejecutan en el backend. Allí usamos `RabbitListenerEndpointRegistry`, que conoce los contenedores creados por `@RabbitListener`. El BFF expone el control a React y reenvía el JWT verificado; ambos servicios comprueban los roles.

La API usa `pause` y `resume` como nombres de operación. En Spring AMQP se implementan con `stop(callback)` y `start()`. El callback permite distinguir una pausa solicitada de una pausa terminada. Véase la [documentación oficial de gestión de contenedores](https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-annotation-driven/container-management.html).

La lógica está en `ListenerControlService`; el controlador HTTP devuelve su resultado. Cada listener tiene su propio control sincronizado. Solicitudes repetidas son idempotentes y una reanudación durante una pausa pendiente devuelve `409`.

Solo admitimos tres IDs definidos en `RabbitListenerIds`, compartidos con las anotaciones:

| ID | Responsabilidad |
| --- | --- |
| `receptionNotifications` | Avisos de reservas para recepción |
| `guestNotifications` | Avisos destinados al huésped |
| `deliveryDemo` | Prueba aislada, cuando `MESSAGING_DEMO_ENABLED=true` |

Las colas se consultan desde el contenedor real. Así, los IDs permanecen estables aunque un entorno de pruebas utilice nombres de cola temporales. Un ID desconocido o un consumidor opcional deshabilitado devuelve `404`.

## Estados y respuestas

| Estado | Texto en pantalla | Significado |
| --- | --- | --- |
| `RUNNING` | Iniciado | El contenedor está iniciado. La conexión efectiva al broker se comprueba en **Entrega de avisos**. |
| `STOPPING` | Pausando… | La parada todavía está pendiente; la reanudación se bloquea temporalmente. |
| `STOPPED` | En pausa | Terminó la parada del contenedor; se puede solicitar su reanudación. |

Una pausa pendiente responde `202 Accepted`. Una pausa ya completada o repetida responde `200 OK`. El BFF conserva ese código. Los errores de inicio/parada responden `503` en el backend; el BFF utiliza su manejo existente de indisponibilidad y devuelve `502` con un mensaje genérico.

La pantalla consulta cada diez segundos. Si falla la consulta, muestra el error. Una lectura iniciada antes de una acción se descarta para que no reemplace el nuevo estado con información anterior.

## Qué ocurre con los mensajes

La parada normal permite terminar las entregas ya recibidas, incluidos los mensajes prefetched, antes de completar el callback. Conservamos `prefetch=1` y el ACK manual existente después del commit.

La parada tiene el tiempo máximo de cierre del contenedor. Si una entrega no termina dentro de ese tiempo, Spring puede cerrar su canal y RabbitMQ reentrega lo pendiente de ACK. La deduplicación de inbox que implementamos antes protege el efecto de negocio frente a esa reentrega.

Los mensajes nuevos esperan en su cola. Siguen aplicándose el TTL, el límite de mensajes y el DLX/DLQ actuales: una pausa prolongada puede producir expiraciones. La pausa se guarda en memoria; al reiniciar el backend se aplica el arranque normal de sus consumidores.

Este control afecta a los listeners de esta instancia del backend. Para desplegar varias instancias habrá que decidir cómo coordinar las órdenes entre ellas.

## API

El frontend utiliza el prefijo `/api/bff/messaging/listeners`. El backend recibe `/api/messaging/listeners`.

| Método | Ruta del BFF | Roles | Resultado |
| --- | --- | --- | --- |
| GET | `/api/bff/messaging/listeners` | Admin, Recepcionista | Lista de consumidores habilitados |
| GET | `/api/bff/messaging/listeners/{id}` | Admin, Recepcionista | Estado de un consumidor |
| POST | `/api/bff/messaging/listeners/{id}/pause` | Admin | Pausa gradual; `200` o `202` |
| POST | `/api/bff/messaging/listeners/{id}/resume` | Admin | Inicio; `200`, o `409` si sigue pausando |

Las operaciones POST no requieren body. Ejemplo de respuesta individual:

```json
{
  "id": "receptionNotifications",
  "label": "Avisos de recepción",
  "queues": ["andesstay.reservations"],
  "state": "STOPPED"
}
```

Los nombres físicos de cola dependen del catálogo `config/rabbit-topology.properties`; el ejemplo ilustra la estructura de la respuesta.

## Cómo probarlo desde AndesStay

1. Inicia Docker Desktop y ejecuta `scripts/start-local.cmd` desde la raíz del repositorio.
2. Entra en `http://localhost:5173/dashboard` con una cuenta que tenga el rol Admin de la API.
3. En **Control de avisos**, pausa **Avisos de recepción**. Espera hasta ver **En pausa**.
4. Crea una reserva de prueba con fechas y disponibilidad válidas.
5. Consulta **Entrega de avisos** o la cola de recepción en RabbitMQ: el evento queda esperando. El consumidor de huéspedes sigue iniciado; la entrega de ese aviso requiere una reserva asociada a una cuenta de huésped, como en los pasos anteriores.
6. Reanuda recepción y actualiza los avisos. El evento se procesa y aparece su notificación.
7. Entra como Recepcionista: se muestran estados y el botón de consulta, sin botones de pausa/reanudación.

También existe una prueba automática con RabbitMQ real y dos colas temporales. Puede ejecutarse sin MySQL ni Kafka:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d --wait rabbitmq
.\scripts\test-listener-control.cmd
```

La prueba verifica una entrega retenida sin ACK, la pausa pendiente, el rechazo de una reanudación anticipada, el procesamiento independiente de huéspedes, un mensaje acumulado y su entrega al reanudar. Las colas temporales se eliminan al terminar y tienen caducidad adicional para una interrupción del proceso.

Para las pruebas normales y la construcción de todos los módulos:

```powershell
.\scripts\test-local.cmd
```

El respaldo previo está en `backup/code-step-18-listener-control/`, excluido de Git.

## Verificación del 9 de octubre de 2026

- Backend: 234 pruebas aprobadas. Cuatro pruebas dependientes de servicios externos se omitieron en esa ejecución normal.
- BFF: 69 pruebas aprobadas, incluyendo JWT reenviado, permisos, conservación del `202`, errores `404`/`409` y respuesta segura ante indisponibilidad.
- Frontend: 45 pruebas aprobadas, siete nuevas para el control de consumidores. ESLint sin advertencias y construcción de producción correcta.
- RabbitMQ real: una prueba adicional aprobada con pausa pendiente, ACK, acumulación, independencia entre audiencias y reanudación. Se verificó la eliminación de las dos colas temporales.
- Entorno actualizado: los seis servicios principales están saludables y `rabbit-setup` terminó correctamente. Cada cola de trabajo tiene un consumidor, con cero mensajes pendientes o sin ACK. Una consulta a la nueva API a través de Nginx y BFF, sin JWT, devolvió `401`.

La comprobación visual con una cuenta Admin también se completó. Al pausar recepción, el dashboard mostró «Entrega en pausa» y RabbitMQ confirmó cero consumidores en `andesstay.reservations`, mientras huéspedes conservó uno. Al reanudar, recepción volvió a tener un consumidor. Se pausó también la demostración, se envió una prueba aislada y se reanudó su consumidor: el dashboard mostró el mensaje de prueba en cuarentena, como corresponde al fallo controlado. Los tres consumidores quedaron iniciados y no se crearon reservas.

RabbitMQ Management quedó abierto en la cola de recepción, con `Consumers=1`, `Ready=0` y `Unacked=0`. Las capturas privadas están en `.validation/step-18-ui-paused.jpg` y `.validation/step-18-ui-rabbit-active.jpg`; el recuento del broker durante la pausa está en `.validation/step-18-ui-reception-paused.txt`.

Los logs están en `.validation/step-18-backend-tests.log`, `.validation/step-18-bff-tests.log`, `.validation/step-18-frontend-test.log`, `.validation/step-18-frontend-lint.log`, `.validation/step-18-frontend-build.log`, `.validation/listener-control-real-tests.log` y `.validation/step-18-runtime-start.log`.

Durante la verificación, el entorno restringido de Windows bloqueó el enlace interno de Mockito, la conexión interna del cliente HTTP y los archivos temporales compartidos de Vitest. El backend se verificó precargando el agente Mockito de la versión instalada; BFF, frontend y la prueba de broker se ejecutaron con el acceso local necesario. Estas restricciones no requirieron cambiar la configuración de la aplicación.

## Orden recomendado para continuar

1. **Control de consumidores:** implementado en este paso.
2. **Medir procesamiento y carga:** implementado en el [paso 17](17-rendimiento-rabbitmq.md), con lotes sintéticos, pausas, distintos prefetch y concurrencia. Registra tiempos, tasas y comprobaciones de entrega. La medición con transacciones y avisos reales queda como ampliación posterior.
3. **Versionar la API y completar el gateway:** `/api/v1` se incorpora en el [paso 18](18-api-versionada.md), con JWT y alias de compatibilidad. El [paso 19](19-limites-api-local.md) añade límites de solicitudes, respuesta HTTP 429 y verificación con Nginx real en local.
4. **Cluster y recuperación:** implementado en el [paso 20](20-cluster-rabbitmq-recuperacion.md) como laboratorio local de tres nodos, colas quorum, caída del líder, reconexión, reentrega y recuperación de mayoría con deduplicación. El arranque habitual de la aplicación conserva su broker de un nodo.
5. **Base de datos en la nube:** completar el requisito explícito de EP3 con migraciones, credenciales externas al repositorio y conexión protegida.
6. **Evidencia final:** matriz y registro de pruebas incorporados en el [paso 21](21-evidencias-ep3.md), con comprobaciones automáticas de las cuatro cuentas y el CRUD real actualizado. Quedan el alta externa completa, los recorridos con Microsoft real y la publicación de la versión revisada en GitHub.

Extraer los avisos a un microservicio propio puede facilitar la evolución posterior. Es una decisión de arquitectura; no lo presentamos como un requisito de la rúbrica por ese nombre.
