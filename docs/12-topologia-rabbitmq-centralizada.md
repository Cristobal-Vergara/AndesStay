# Paso 12: una fuente compartida para la topología RabbitMQ

Primera etapa de adaptación de AndesStay a la EP3. El objetivo es centralizar los nombres de colas y exchanges y las routing keys que definen sus bindings, conservando el flujo actual de reservas y avisos.

## 1. Qué problema había

El backend tenía constantes y valores de respaldo en `RabbitMQConfig`, las routing keys también estaban en `ReservationEventType`, Rabbit Admin consultaba un nombre escrito directamente en el controlador y Docker repetía nombres en sus políticas JSON.

Cambiar una cola en un solo lugar podía dejar el productor, los bindings o el DLX apuntando a destinos diferentes. Tener una clase de configuración no evitaba esa duplicación entre aplicaciones.

## 2. Dónde está ahora la configuración

`config/rabbit-topology.properties` es la fuente compartida. Contiene:

- Colas de recepción y huéspedes y exchange de reservas.
- Routing keys para creación, confirmación y cancelación.
- DLX y DLQ reales.
- Nombres de políticas, TTL y límite de mensajes.
- Nombres y retención de la demostración aislada.

Se utiliza `.properties`, el formato de configuración que ya usa el proyecto. Spring lo importa desde `application.properties`; cumple la función de una configuración de aplicación centralizada. El catálogo es obligatorio: si falta, la aplicación falla al arrancar en lugar de utilizar silenciosamente nombres de respaldo.

Los bindings se identifican por exchange, cola y routing key. Sus valores proceden del catálogo y sus declaraciones permanecen en `RabbitMQConfig`. Recepción recibe creación, confirmación y cancelación; el huésped recibe confirmación y cancelación.

## 3. Cómo lo usan las aplicaciones Java

Los POM del backend y Rabbit Admin incluyen el mismo archivo raíz como recurso del classpath. Así, cada JAR contiene la configuración sin mantener una copia editable dentro de cada módulo.

`RabbitMQConfig` construye un mapa de tipo de evento a routing key usando las propiedades. El productor y los bindings reciben ese mismo mapa mediante inyección. `ReservationEventType` conserva los estados del negocio y deja de contener nombres de transporte.

Rabbit Admin obtiene de las propiedades el nombre que consulta en `/api/rabbitmq/status`. Los consumidores y el panel de estado siguen utilizando los nombres de los beans Queue.

## 4. Cómo se mantienen alineadas las políticas de Docker

`scripts/export-rabbit-topology.ps1` lee el catálogo y genera `.validation/rabbitmq-definitions.json`. Ese archivo derivado contiene DLX, DLQ, binding de cuarentena y las dos políticas reales. No se edita manualmente ni se sube a GitHub.

El script comprueba propiedades requeridas, nombres, duplicaciones y límites positivos antes de escribir. Las expresiones de selección de colas escapan los puntos y coinciden con nombres completos: la política de una cola no alcanza otra con un nombre parecido.

`start-local.cmd` genera las definiciones antes de ejecutar Compose. El trabajo `rabbit-setup` las importa en el broker. Compose exige que el archivo exista, evitando crear una carpeta por error cuando falta el archivo.

Los Dockerfile del backend y Rabbit Admin ahora usan el contexto raíz para poder incluir el catálogo. La `.dockerignore` raíz permite únicamente sus fuentes y esa configuración pública; excluye el resto, incluidos secretos y respaldos.

## 5. Qué conserva esta etapa

- Los nombres de los recursos existentes y el contenido de los eventos.
- La persistencia de reservas y la deduplicación de avisos.
- Los permisos Microsoft Entra/JWT y la separación entre avisos de cada huésped.
- Las políticas reales: 24 horas de entrega, 10.000 mensajes por cola y 7 días de cuarentena.
- La demostración: 10 segundos en origen y 60 segundos en su DLQ, habilitada solo cuando se configura expresamente.

Esta etapa centraliza la configuración. ACK manual, organización por dominio, servicio independiente de notificaciones y CRUD del administrador pertenecen a las etapas siguientes.

## 6. Cómo ejecutar y comprobar

Desde la raíz del repositorio:

```powershell
.\scripts\start-local.cmd
.\scripts\test-local.cmd
.\scripts\test-mysql.cmd
```

La tercera orden requiere Docker iniciado y el respaldo local `backup/andesstay.sql`, como antes. Utiliza esquemas y recursos temporales de prueba.

Para preparar o comprobar únicamente las definiciones del broker:

```powershell
.\scripts\export-rabbit-topology.cmd
.\scripts\export-rabbit-topology.cmd -Check
```

Antes de usar Compose directamente, genera las definiciones. Por ejemplo, para los administradores opcionales:

```powershell
.\scripts\export-rabbit-topology.cmd
docker compose --env-file .env -f docker/docker-compose.yml --profile operations up --build -d --wait
```

Si se modifica el catálogo, hay que reconstruir los JAR/imágenes y volver a aplicar las definiciones mediante el arranque. Renombrar recursos que ya contienen mensajes requiere una migración propia; centralizar su nombre no traslada mensajes de una cola a otra.

## 7. Evidencias de esta etapa

- `RabbitTopologyContractTests`: verifica que Maven empaqueta el catálogo sin cambios, conserva los recursos actuales y alinea productor y bindings cuando cambian nombres y routing keys en un contexto aislado.
- `RabbitTopologyControllerTests`: verifica que Rabbit Admin consulta una cola configurada con un nombre diferente.
- `scripts/test-rabbit-topology.ps1`: comprueba políticas y límites originales, regex y destinos después de un cambio de nombres, y rechazo de una configuración inválida sin sobrescribir el JSON válido.
- Las pruebas existentes mantienen la comprobación de reservas, privacidad, confirmaciones, reintentos, DLQ y retención.

Verificación del 8 de octubre de 2026:

| Comprobación | Resultado |
| --- | --- |
| Backend, ejecución normal | 189 aprobadas; las 3 pruebas externas se ejecutaron en la suite MySQL/brokers |
| BFF | 61 aprobadas |
| Kafka Admin | 1 aprobada |
| Rabbit Admin | 2 aprobadas |
| Frontend | 38 aprobadas; ESLint y build correctos |
| Generación de políticas | 3 escenarios correctos |
| Empaquetado de backend y Rabbit Admin | Ambos JAR contienen el catálogo, verificado por SHA-256 |
| Configuración Compose | Válida |
| MySQL/RabbitMQ/Kafka reales | 34 aprobadas; esquemas, colas y políticas temporales eliminados; respaldo SQL sin cambios |
| Reconstrucción y arranque Docker | Imágenes de backend y Rabbit Admin construidas; 7 servicios saludables |
| HTTP real | Frontend 200; mensajería BFF y Rabbit Admin devuelven 401 sin JWT |

Las pruebas de Java necesitaron ejecutarse fuera del entorno restringido del agente: allí Mockito no podía conectar su instrumentación por la tubería local de Java. La ejecución normal del sistema pasó. No se modificó el mecanismo de pruebas para eludir ese problema.

Docker Desktop no iniciaba su motor Linux; el reinicio realizado por el usuario permitió completar la verificación real.

La primera ejecución con brokers detectó que la prueba de políticas agotaba una espera de cinco segundos al observar el nuevo límite de mensajes. Se ajustó únicamente esa prueba: antes de publicar comprueba los valores efectivos de TTL, límite y DLX, compara los números sin depender de su tipo Java y permite hasta 15 segundos para observar la política. Para la caducidad también comprueba que el consumidor de prueba esté detenido. Se conservan las verificaciones de expiración, desbordamiento, cabeceras `x-death` y retención. La repetición de las 34 pruebas pasó.

El respaldo local de los archivos anteriores está en `backup/code-step-14-rabbit-topology/`, excluido de Git.
