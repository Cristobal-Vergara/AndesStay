# Paso 19: límites de solicitudes en la API local

## Qué cambia y por qué

Nginx sirve React y reenvía las llamadas `/api/` al BFF. Ahora controla cuántas
solicitudes llegan desde una misma IP. Una ráfaga excesiva recibe **HTTP 429
Too Many Requests** antes de alcanzar el BFF. Esto reduce el trabajo que generan
consultas repetidas por error o automatizaciones demasiado rápidas.

El recorrido es: navegador → Nginx → BFF → backend. Microsoft Entra sigue
autenticando las cuentas, y BFF/backend mantienen la validación del JWT y los
permisos. Una solicitud dentro de la cuota todavía necesita autorización; una
solicitud fuera de la cuota puede recibir 429 antes de validar su identidad.

Este paso completa el control del punto de entrada previsto después de versionar
la API. Se aplica al entorno Docker local de AndesStay.

## Límites elegidos

| Parámetro | Valor | Motivo |
|---|---|---|
| Ritmo por IP | 10 solicitudes por segundo | Margen para consultas de pantalla y estados que se actualizan cada diez segundos |
| Ráfaga adicional | 20 solicitudes | Permitir varias consultas simultáneas al cargar una pantalla |
| Procesamiento | `nodelay` | Rechazar el exceso sin mantener consultas esperando en Nginx |
| Respuesta al exceso | HTTP 429 | Identificar el límite de solicitudes |
| Espera recomendada | `Retry-After: 3` | Dar tiempo a que se libere el margen si el cliente deja de enviar solicitudes |
| Memoria compartida | 10 MB | Mantener las cuotas entre los workers de esta instancia de Nginx |

Nginx aplica un algoritmo de *leaky bucket*: el margen se recupera con el tiempo.
No es una ventana que se vacía al cambiar el segundo. Con el margen libre puede
aceptar aproximadamente 21 solicitudes instantáneas; mientras pasa el tiempo
se libera capacidad al ritmo configurado. Véase la
[documentación oficial de limitación de Nginx](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html).

Estos valores son un punto inicial para desarrollo, no una capacidad certificada
del backend. La medición del paso 17 evalúa consumidores RabbitMQ y no determina
cuántas llamadas HTTP soporta toda la aplicación.

## Cómo está implementado

`AndesStay Frontend/nginx.conf` define la zona `andesstay_api` con la clave
`$binary_remote_addr`: la IP de la conexión que ve Nginx. No usa el JWT sin
verificar ni las cabeceras `X-Forwarded-For` o `X-Real-IP` enviadas por el cliente
para decidir su cuota.

El límite se aplica a toda la ubicación `/api/`, a todos sus métodos y rutas.
`/api/v1/bff/...` y `/api/bff/...` comparten la misma zona. Cambiar de endpoint,
alias o token no crea una cuota nueva para la misma IP.

Las páginas, las rutas SPA y los archivos JavaScript/CSS están fuera de ese
límite. La comprobación de salud de Nginx también puede continuar cuando la API
rechaza solicitudes por exceso.

Una ubicación interna devuelve un error JSON:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 3
Cache-Control: no-store
```

```json
{
  "type": "about:blank",
  "title": "Demasiadas solicitudes",
  "status": 429,
  "detail": "Se enviaron demasiadas solicitudes. Espera 3 segundos y vuelve a intentarlo."
}
```

`src/api.js` conserva el código 429 y muestra el mensaje en los componentes que
ya presentan errores. También conserva una cabecera `Retry-After` numérica válida
en `error.retryAfterSeconds`. Si la respuesta no contiene JSON, muestra un aviso
genérico de espera. No reenvía automáticamente la solicitud: repetir un POST de
reservas sin una estrategia de idempotencia podría duplicar una operación.

El proxy conserva las respuestas del BFF. Por ejemplo, un 409 por un conflicto
de reserva mantiene su contenido. Incluso un 429 emitido por el propio BFF
conserva sus cabeceras; no se reemplaza por el mensaje del limitador de Nginx.

## Cómo comprobarlo

Con Docker Desktop en modo Linux y Node 24 instalados, ejecuta desde la raíz:

```powershell
.\scripts\test-api-gateway.cmd
```

No es necesario detener AndesStay. El script crea un proyecto Docker con nombre
aleatorio, dos contenedores y su propia red. Monta el archivo real `nginx.conf`,
usa un BFF de prueba y publica un puerto aleatorio exclusivamente en localhost.
No lee `.env` ni usa credenciales, reservas, MySQL o los brokers. La primera
ejecución descarga las imágenes oficiales de Nginx y Node si faltan.

Comprueba seis escenarios:

1. Nginx acepta la configuración con `nginx -t`.
2. Las rutas v1 y el alias conservan el método, el body y la cabecera Authorization.
3. Las respuestas 401, 409 y 429 del BFF se conservan.
4. Una ráfaga de 100 llamadas que mezcla alias, métodos y cabeceras con IP falsas
   recibe 429 para el exceso. El contador del BFF confirma que las rechazadas no
   llegaron al servicio. Comprueba el JSON y las cabeceras del limitador.
5. La página y el contenido estático siguen devolviendo 200 durante esa carga.
6. Después de esperar tres segundos, vuelve a aceptar una solicitud de API.

El BFF de prueba solo comprueba el contrato HTTP del proxy; no valida identidades
Microsoft ni sustituye las pruebas de seguridad de Java. El script elimina sus
contenedores y red al terminar, también si falla una comprobación, y registra el
resultado en `.validation/api-gateway-latest.json`. Si falla la limpieza, informa
el nombre exacto de su proyecto temporal.

Para actualizar la aplicación y verla en `http://localhost:5173`:

```powershell
.\scripts\start-local.cmd
```

Una consulta normal sin sesión a `/api/v1/bff/reservations` sigue devolviendo
401. Para observar la ráfaga usa la prueba aislada anterior, que evita ocupar la
cuota de tu navegador. Los logs del frontend registran los rechazos del limitador:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml logs --tail 30 frontend
```

## Alcance y siguiente paso

La cuota es por IP, no por cuenta. Usuarios detrás de la misma red pueden
compartirla; Docker Desktop también puede presentar varias conexiones con una
misma dirección. Si se incorpora otro proxy, habrá que configurar explícitamente
qué direcciones se confían para obtener la IP real.

Cada instancia de Nginx tiene sus propias cuotas. Esta configuración no es un
limitador distribuido ni una solución completa ante ataques de red. El servidor
de desarrollo Vite (`npm run dev`) y el acceso directo a servicios de operación
opcionales no pasan por este Nginx y no reciben este límite.

El siguiente paso del plan local es un entorno separado con varios nodos de
RabbitMQ, para comprobar recuperación ante una caída y reentrega sin duplicar
avisos. El respaldo previo de este paso está en
`backup/code-step-21-api-rate-limit/`, excluido de Git.

## Verificación del 13 de octubre de 2026

Las 52 pruebas del frontend pasaron, incluyendo el aviso 429, la conservación de
la espera, cabeceras inválidas y la ausencia de reenvío automático de una reserva.
ESLint pasó sin advertencias. Los seis escenarios con Nginx real también pasaron:
21 de las 100 solicitudes de la ráfaga llegaron al BFF de prueba y 79 se
rechazaron. La cantidad exacta puede variar según el tiempo observado en el equipo.
El entorno temporal se eliminó al terminar.

La construcción de producción y la imagen Docker del frontend también pasaron.
Se actualizó únicamente su contenedor. Los seis servicios principales quedaron
saludables; se verificó que los otros cinco conservaron sus identificadores.
El archivo Nginx dentro del contenedor coincide con el código comprobado.
En HTTP real, `/login` devolvió 200, las reservas por v1 y alias devolvieron 401
sin JWT, y un token inválido recibió 401. Las respuestas normales no incluyen
`Retry-After`. El resumen está en `.validation/step-21-runtime-verification.json`
y los logs de pruebas, ESLint, build y arranque están en `.validation/step-21-*`.
