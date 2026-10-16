import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { randomBytes, randomUUID } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const validation = join(root, ".validation");
mkdirSync(validation, { recursive: true });
const project = `andesstay-cluster-test-${randomUUID().slice(0, 8)}`;
const runId = randomUUID();
const environment = { ...process.env, CLUSTER_PROJECT: project, CLUSTER_RUN_ID: runId,
    CLUSTER_USERNAME: "cluster-test", CLUSTER_PASSWORD: randomBytes(24).toString("hex"),
    CLUSTER_COOKIE: randomBytes(24).toString("hex"), TEST_RABBITMQ_CLUSTER: "true",
    CLUSTER_MAVEN_LOG: join(validation, "rabbit-cluster-tests.log"),
    CLUSTER_TEST_REPORT: join(validation, `rabbit-cluster-${runId}.json`),
};
const args = ["compose", "--env-file", join(root, "docker/tests/compose.env"), "-p", project, "-f", join(root, "docker/tests/rabbit-cluster/docker-compose.yml")];
const docker = (...tail) => execFileSync("docker", tail, { cwd: root, env: environment, encoding: "utf8", timeout: 180000 });
const compose = (...tail) => docker(...args, ...tail);
const summary = { checkedAt: new Date().toISOString(), project, runId, success: false };
const port = (service, target) => {
    const address = compose("port", service, String(target)).trim();
    assert.match(address, /^127\.0\.0\.1:\d+$/);
    return Number(address.split(":")[1]);
};

try {
    console.log("Preparando tres nodos RabbitMQ 4.1.8 en un proyecto independiente...");
    compose("config", "--quiet");
    compose("up", "-d", "--wait", "--wait-timeout", "120");
    const endpoints = {};
    for (const service of ["rabbit1", "rabbit2", "rabbit3"]) {
        endpoints[service] = { amqp: port(service, 5672), management: port(service, 15672), containerId: compose("ps", "-q", service).trim() };
    }
    environment.CLUSTER_ENDPOINTS_JSON = JSON.stringify(endpoints);
    console.log("Cluster iniciado. Ejecutando caida del lider, reentrega y perdida de mayoria...");
    execFileSync("powershell.exe", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", join(root, "scripts/invoke-rabbit-cluster-tests.ps1")], {
        cwd: root, env: environment, encoding: "utf8", timeout: 300000, stdio: "inherit",
    });
    const report = JSON.parse(readFileSync(environment.CLUSTER_TEST_REPORT, "utf8"));
    assert.equal(report.runId, runId);
    assert.equal(report.success, true);
    summary.test = report;
    summary.success = true;
} catch (error) {
    summary.error = error.message;
    console.error("La prueba fallo. Los detalles de Java quedan en .validation/rabbit-cluster-tests.log.");
    process.exitCode = 1;
} finally {
    try {
        // Verificar la propiedad antes de eliminar unicamente los datos de este test.
        assert.match(project, /^andesstay-cluster-test-[a-f0-9]{8}$/);
        const volumes = docker("volume", "ls", "-q", "--filter", `label=com.docker.compose.project=${project}`).trim().split("\n").filter(Boolean);
        for (const volume of volumes) {
            assert.ok(volume.startsWith(project + "_rabbit"));
            const labels = JSON.parse(docker("volume", "inspect", volume, "--format", "{{json .Labels}}"));
            assert.equal(labels["com.docker.compose.project"], project);
        }
        compose("down", "--volumes", "--timeout", "5");
        assert.equal(compose("ps", "-aq").trim(), "");
        assert.equal(docker("volume", "ls", "-q", "--filter", `label=com.docker.compose.project=${project}`).trim(), "");
        assert.equal(docker("network", "ls", "-q", "--filter", `label=com.docker.compose.project=${project}`).trim(), "");
        summary.cleaned = true;
    } catch (error) {
        summary.cleaned = false;
        summary.success = false;
        summary.cleanupError = error.message;
        console.error(`No se pudo limpiar el proyecto temporal ${project}.`);
        process.exitCode = 1;
    }
    writeFileSync(join(validation, "rabbitmq-cluster-latest.json"), JSON.stringify(summary, null, 2) + "\n");
}
if (summary.success) {
    const evidence = join(root, ".ecc/evidence");
    mkdirSync(evidence, { recursive: true });
    writeFileSync(join(evidence, "rabbitmq-cluster-latest.json"), JSON.stringify(summary, null, 2) + "\n");
    console.log(`Cluster verificado: ${summary.test.uniqueEvents} eventos, ${summary.test.notices} avisos, sin duplicados. Tres nodos y volumenes temporales eliminados.`);
}
