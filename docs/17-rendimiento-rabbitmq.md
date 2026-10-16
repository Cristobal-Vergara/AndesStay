# Paso 17: medir el procesamiento de mensajes RabbitMQ

## Qué queremos comprobar

El paso anterior mostró cómo pausar y reanudar consumidores. Ahora comparamos cuánto tarda en vaciarse un lote de trabajo cuando cambia el número de consumidores o el prefetch.

Usamos mensajes que representan avisos de reservas, con trabajo simulado. La prueba no inicia el backend completo, no consulta MySQL, no crea reservas y no utiliza las colas de recepción, huéspedes o demostración. Se conecta al RabbitMQ local y crea recursos independientes para cada lote.

Es una medición del procesamiento de mensajería en este equipo. No representa la capacidad total de AndesStay ni mide el tiempo que tarda un cliente en reservar.

## Consumidores y prefetch

Un consumidor ejecuta el trabajo de un mensaje. Varios consumidores pueden repartir el lote y trabajar al mismo tiempo. El prefetch limita las entregas sin confirmar que RabbitMQ permite por consumidor: subirlo permite recibir trabajo por adelantado, pero no crea más trabajadores. Véase la [documentación oficial de RabbitMQ](https://www.rabbitmq.com/docs/consumer-prefetch).

Comparamos las cuatro combinaciones para distinguir el efecto de cada variable:

| Consumidores | Prefetch por consumidor | Qué permite observar |
| ---: | ---: | --- |
| 1 | 1 | Referencia: trabajo secuencial, una entrega sin confirmar |
| 1 | 10 | Efecto de recibir mensajes por adelantado sin aumentar trabajadores |
| 3 | 1 | Efecto de repartir el trabajo entre tres consumidores |
| 3 | 10 | Combinación de trabajo concurrente y entregas por adelantado |

Spring AMQP configura estas opciones en el contenedor de prueba. El modo `MANUAL` utiliza el mismo `RabbitDeliveryHandler` de nuestros avisos. Su [documentación de contenedores](https://docs.spring.io/spring-amqp/reference/amqp/containerAttributes.html) describe concurrencia, prefetch y ACK.

## Cómo se hace una comparación justa

1. Se calienta la JVM y la conexión con 30 mensajes por combinación. Esos resultados se descartan.
2. Se ejecutan tres rondas de medición. En cada ronda cambia el orden de las combinaciones para reducir el sesgo de ejecutar siempre una primero.
3. Cada lote contiene 300 mensajes persistentes de 256 bytes. El trabajo simulado espera 5 ms por mensaje; la duración real depende también del planificador del sistema operativo.
4. Con el contenedor detenido, se publica el lote completo. Se usan publisher confirms y publicación `mandatory`, y se verifica que todos los mensajes estén esperando en la cola.
5. Se inicia el contenedor y se espera a que todos sus consumidores estén conectados y detenidos en una barrera de inicio. No han realizado todavía el trabajo ni enviado ACK.
6. Se libera la barrera y se mide con `System.nanoTime()` hasta el último envío local de ACK. Publicación, conexión, arranque y limpieza quedan fuera de ese intervalo.
7. Se comprueban los identificadores esperados, un efecto por mensaje y un ACK local por efecto. Después de detener el contenedor, la cola debe tener cero consumidores y cero mensajes pendientes. Una entrega sin ACK volvería a quedar en la cola tras el cierre.
8. Se elimina la cola y se verifica su desaparición antes de aceptar el resultado.

La barrera permite empezar con consumidores preparados y trabajo disponible. Con prefetch mayor que uno pueden existir entregas adicionales en espera dentro del cliente; eso forma parte de la configuración que se compara.

El ACK local indica que el cliente envió la confirmación; no es un recibo independiente del broker para cada ACK. La comprobación posterior con el contenedor detenido verifica que no quedan entregas recuperadas en la cola. [RabbitMQ distingue los ACK de consumidor de los publisher confirms](https://www.rabbitmq.com/docs/confirms).

## Qué resultados se guardan

- Tiempo del lote y mensajes por segundo de cada repetición.
- Mediana del tiempo y de la tasa para cada combinación, con mínimo y máximo de la tasa.
- P50 y P95 del tiempo desde la liberación del lote hasta el ACK de cada mensaje. Incluyen espera por el turno de procesamiento; no son la duración individual del trabajo de negocio.
- P50 y P95 de la espera simulada realmente observada, para distinguir cambios de planificación del sistema operativo de los cambios de mensajería.
- Mensajes publicados, mensajes esperando durante la pausa, identificadores procesados, efectos duplicados y envíos locales de ACK.
- Versión de RabbitMQ, Java y sistema operativo, parámetros de trabajo y fecha UTC.
- Confirmación de que se eliminaron todas las colas temporales.

No imponemos que una combinación sea más rápida: el rendimiento depende del equipo y de la carga. La prueba falla por mensajes faltantes, efectos repetidos, fallos de consumidores, problemas de ACK o limpieza. Una variación de velocidad se registra para analizarla.

El informe completo se genera en `.validation/rabbit-benchmark.json`. Solo si la ejecución termina correctamente, el script lo copia a `.ecc/benchmarks/rabbitmq-latest.json`, que puede versionarse y no contiene credenciales ni datos de huéspedes. El log local queda en `.validation/rabbit-benchmark-tests.log`. Una ejecución fallida conserva la última medición válida; el script identifica cada ejecución para impedir que un informe anterior se presente como nuevo.

## Cómo ejecutarlo

Necesitas JDK 17, el entorno `.env` preparado y RabbitMQ local iniciado. Desde la raíz del repositorio:

```powershell
.\scripts\test-rabbit-benchmark.cmd
```

Si RabbitMQ está detenido, puedes iniciar únicamente ese servicio:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d --wait rabbitmq
```

Para cambiar el tamaño del lote, las repeticiones o el trabajo simulado:

```powershell
.\scripts\test-rabbit-benchmark.cmd -Messages 600 -Repeats 3 -WorkMs 5
```

Los rangos permitidos son 30–3000 mensajes, 1–10 repeticiones y 1–25 ms de trabajo. El calentamiento permanece en 30 mensajes por combinación. Con más carga la ejecución tarda más y comparte recursos del equipo con el resto de aplicaciones.

Las colas tienen nombres aleatorios con prefijo `andesstay.benchmark.` y no tienen bindings con nuestros exchanges de reservas. La eliminación ocurre en `finally`; una caducidad de 10 minutos sin uso sirve de respaldo si se interrumpe el proceso. Los mensajes tienen TTL de 5 minutos. Estas configuraciones pertenecen exclusivamente a la prueba.

En RabbitMQ Management puedes observarlas temporalmente en **Queues and Streams** filtrando por `andesstay.benchmark.`. Las pausas son breves y automáticas; el resultado numérico queda guardado aunque no alcances a ver la cola en pantalla.

## Cómo aplicarlo a AndesStay

La configuración normal de avisos conserva `prefetch=1`. Aumentar concurrencia puede beneficiar trabajos que esperan recursos externos, pero también aumentar la presión sobre MySQL. Aumentar prefetch puede mejorar el aprovechamiento del canal, pero reserva más entregas en cada consumidor y puede afectar el reparto y el orden de finalización.

Antes de cambiar la configuración operativa, necesitamos una medición con avisos reales, sus transacciones y la deduplicación de inbox. La ausencia de duplicados en esta prueba describe esta ejecución normal; no sustituye nuestras comprobaciones de reentrega y deduplicación ante fallos.

Esta etapa aplica las guías de carga y consumidores. La EP3 también exige la integración con una base de datos en la nube, que sigue siendo un paso pendiente.

## Verificación

Verificación del 9 de octubre de 2026 con RabbitMQ 4.1.8, Java 17.0.19 y Windows 11. Cada combinación tuvo tres repeticiones de 300 mensajes; se procesaron 3600 mensajes medidos y 120 de calentamiento.

| Consumidores | Prefetch | Mediana del lote | Mediana msg/s | Rango msg/s entre repeticiones |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 1 | 4673,3 ms | 64,2 | 64,2–64,3 |
| 1 | 10 | 4677,2 ms | 64,1 | 64,1–64,2 |
| 3 | 1 | 1557,6 ms | 192,6 | 192,6–193,5 |
| 3 | 10 | 1552,9 ms | 193,2 | 192,7–193,7 |

En este trabajo simulado, tres consumidores redujeron la mediana del tiempo a aproximadamente un tercio. Cambiar prefetch de 1 a 10 produjo una diferencia pequeña en la mediana. Esto no basta para recomendar esos ajustes para las transacciones reales del sistema.

Las medianas de la espera observada estuvieron entre 14,1 y 15,4 ms, aunque se solicitaron 5 ms. El planificador y el entorno local influyen en las cifras. Una ejecución previa también mostró variación mayor entre repeticiones; se incorporó la duración observada del trabajo para poder distinguir ese efecto. La tabla corresponde a la ejecución final, cuyos datos completos y fecha están en `.ecc/benchmarks/rabbitmq-latest.json`.

- Prueba real: una prueba JUnit aprobada, con las 12 mediciones y las cuatro combinaciones de calentamiento.
- Cada lote medido: 300 esperando durante la pausa, 300 identificadores procesados, cero efectos duplicados y 300 envíos locales de ACK.
- Después de detener cada contenedor: cero consumidores y cero mensajes pendientes; eliminación verificada de su cola.
- Suite normal del backend: 234 pruebas aprobadas, cero fallos y cinco pruebas externas omitidas. La nueva comparación requiere activación explícita y se ejecutó por separado.
- Comando Windows `.cmd`: ejecutado correctamente con sus parámetros por defecto.
- Broker al terminar: solo las cinco colas habituales; recepción, huéspedes y demostración con un consumidor cada una, cero mensajes listos y cero pendientes de ACK; DLQ sin consumidores ni mensajes.
- Servicios locales: backend, BFF, frontend, Kafka, MySQL y RabbitMQ saludables.

Los registros están en `.validation/rabbit-benchmark-tests.log` y `.validation/step-19-backend-tests.log`. La comparación utilizó acceso a RabbitMQ local; la suite normal precargó el agente Mockito de la versión instalada para evitar el enlace dinámico bloqueado en este entorno de Windows.
