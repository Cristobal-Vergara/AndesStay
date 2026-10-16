import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { request } from "node:http";
import { dirname, join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const project = `andesstay-gateway-test-${randomUUID().slice(0, 8)}`;
const composeArgs = ["compose", "--env-file", join(root, "docker/tests/compose.env"), "-p", project, "-f", join(root, "docker/tests/gateway/docker-compose.yml")];
const summary = { checkedAt: new Date().toISOString(), project, scenarios: [] };
const compose = (...args) => execFileSync("docker", [...composeArgs, ...args], {
    cwd: root, encoding: "utf8", timeout: 180000, stdio: ["ignore", "pipe", "pipe"],
});
const statistics = () => JSON.parse(compose("exec", "-T", "bff", "node", "-e",
    "fetch('http://127.0.0.1:8090/_stats').then(r=>r.text()).then(t=>process.stdout.write(t))"));

function http(url, { method = "GET", headers = {}, body } = {}) {
    return new Promise((resolveResponse, reject) => {
        const req = request(url, { method, headers, timeout: 5000 }, res => {
            const chunks = [];
            res.on("data", chunk => chunks.push(chunk));
            res.on("error", reject);
            res.on("end", () => resolveResponse({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks).toString() }));
        });
        req.on("timeout", () => req.destroy(new Error("Tiempo de espera HTTP agotado")));
        req.on("error", reject);
        req.end(body);
    });
}

try {
    console.log("Iniciando Nginx real y BFF de prueba en una red separada (sin .env ni reservas)...");
    compose("up", "-d", "--wait", "--wait-timeout", "60");
    compose("exec", "-T", "frontend", "nginx", "-t");
    summary.scenarios.push("Configuracion real aceptada por nginx -t");
    const binding = compose("port", "frontend", "8080").trim();
    assert.match(binding, /^127\.0\.0\.1:\d+$/);
    const base = `http://${binding}`;
    const token = { Authorization: "Bearer gateway-test" };

    for (const path of ["/api/v1/bff/probe", "/api/bff/probe"]) {
        const result = await http(base + path, { method: "POST", headers: { ...token, "Content-Type": "application/json" }, body: '{"unitId":123}' });
        assert.equal(result.status, 200);
        assert.deepEqual(JSON.parse(result.body), { method: "POST", url: path, authorization: token.Authorization, body: '{"unitId":123}' });
        assert.equal(result.headers["retry-after"], undefined);
    }
    summary.scenarios.push("Rutas v1 y alias conservan metodo, body y Authorization");
    for (const [path, status] of [["probe", 401], ["conflict", 409], ["limited", 429]]) {
        const result = await http(`${base}/api/v1/bff/${path}`);
        assert.equal(result.status, status);
        assert.equal(result.headers["x-test-upstream"], "bff-stub");
        assert.equal(JSON.parse(result.body).detail, `Respuesta de prueba del BFF: ${status}`);
        assert.equal(result.headers["retry-after"], status === 429 ? "7" : undefined);
    }
    summary.scenarios.push("Errores 401, 409 y 429 del BFF se conservan");
    await delay(3000);

    const before = statistics().received;
    const start = performance.now();
    const results = await Promise.all(Array.from({ length: 100 }, (_, i) => http(
        base + (i % 2 ? "/api/bff/probe" : "/api/v1/bff/probe"), {
            method: i % 3 ? "GET" : "POST",
            headers: { ...token, "X-Forwarded-For": `198.51.100.${i + 1}`, "X-Real-IP": `203.0.113.${i + 1}` },
            body: i % 3 ? undefined : "prueba",
        },
    )));
    const elapsedMs = performance.now() - start;
    const passed = results.filter(r => r.status === 200);
    const rejected = results.filter(r => r.status === 429);
    assert.equal(passed.length + rejected.length, results.length);
    assert.ok(passed.length > 0 && rejected.length > 0, "La rafaga debe permitir consultas y rechazar el exceso");
    for (const alias of [0, 1]) assert.ok(results.some((r, i) => i % 2 === alias && r.status === 429), "Un alias no recibe el limite");
    // Margen por el tiempo observado; evita exigir una cifra exacta dependiente del equipo.
    assert.ok(passed.length <= 22 + Math.ceil(10 * elapsedMs / 1000), "Los alias o cabeceras falsas evaden la cuota compartida");
    for (const response of rejected) {
        assert.match(response.headers["content-type"], /^application\/problem\+json/);
        assert.equal(response.headers["retry-after"], "3");
        assert.equal(response.headers["cache-control"], "no-store");
        assert.equal(response.headers["x-test-upstream"], undefined);
        assert.equal(JSON.parse(response.body).status, 429);
        assert.match(JSON.parse(response.body).detail, /Espera 3 segundos/);
    }
    const after = statistics().received;
    assert.equal(after - before, passed.length, "Una solicitud rechazada llego al BFF");
    summary.burst = { sent: results.length, passed: passed.length, rejected: rejected.length, elapsedMs: Math.round(elapsedMs), receivedByBff: after - before };
    summary.scenarios.push("Cuota compartida, cabeceras falsas, JSON 429 y rechazo antes del BFF");

    const pages = await Promise.all(Array.from({ length: 30 }, (_, i) => http(base + (i % 2 ? "/login" : "/index.html"))));
    assert.ok(pages.every(r => r.status === 200 && r.body.includes("contenido estático de prueba")));
    summary.scenarios.push("Pagina, rutas SPA y contenido estatico siguen disponibles");
    await delay(3000);
    assert.equal((await http(base + "/api/v1/bff/probe", { headers: token })).status, 200);
    summary.scenarios.push("La API vuelve a aceptar solicitudes despues de esperar");
    summary.success = true;
} catch (error) {
    summary.success = false;
    summary.error = error.message;
    try { console.error(compose("logs", "--tail", "30")); } catch { /* Conservar el error original. */ }
    throw error;
} finally {
    // El proyecto tiene un nombre aleatorio propio y no comparte volumen con AndesStay.
    try {
        compose("down", "--timeout", "5");
        const remaining = compose("ps", "-aq").trim();
        assert.equal(remaining, "", "Quedaron contenedores temporales");
        const networks = execFileSync("docker", ["network", "ls", "-q", "--filter", `label=com.docker.compose.project=${project}`], {
            encoding: "utf8", timeout: 15000,
        }).trim();
        assert.equal(networks, "", "Quedo una red temporal");
        summary.cleaned = true;
    } catch (cleanupError) {
        summary.cleaned = false;
        summary.success = false;
        console.error(`No se pudo limpiar ${project}: ${cleanupError.message}`);
        process.exitCode = 1;
    }
    const validation = join(root, ".validation");
    mkdirSync(validation, { recursive: true });
    writeFileSync(join(validation, "api-gateway-latest.json"), JSON.stringify(summary, null, 2) + "\n");
}
if (summary.success && summary.cleaned) {
    console.log(`Gateway: ${summary.scenarios.length} escenarios correctos. ${summary.burst.rejected}/${summary.burst.sent} solicitudes de la rafaga rechazadas. Entorno temporal eliminado.`);
}
