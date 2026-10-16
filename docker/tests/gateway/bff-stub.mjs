import { createServer } from "node:http";

// Solo comprueba el contrato HTTP del proxy. No simula la validacion de Entra.
let received = 0;
createServer(async (request, response) => {
    if (request.url === "/_stats") {
        response.setHeader("Content-Type", "application/json");
        response.end(JSON.stringify({ received }));
        return;
    }
    received++;
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    response.setHeader("Content-Type", "application/json");
    response.setHeader("X-Test-Upstream", "bff-stub");
    const status = request.url.endsWith("/conflict") ? 409
        : request.url.endsWith("/limited") ? 429
        : request.headers.authorization === "Bearer gateway-test" ? 200 : 401;
    response.statusCode = status;
    if (status === 429) response.setHeader("Retry-After", "7");
    response.end(JSON.stringify(status === 200
        ? { method: request.method, url: request.url, authorization: request.headers.authorization, body: Buffer.concat(chunks).toString() }
        : { status, detail: `Respuesta de prueba del BFF: ${status}` }));
}).listen(8090, "0.0.0.0");
