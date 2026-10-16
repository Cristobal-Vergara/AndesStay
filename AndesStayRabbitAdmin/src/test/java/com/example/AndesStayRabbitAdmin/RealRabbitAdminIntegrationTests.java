package com.example.AndesStayRabbitAdmin;

import com.example.AndesStayRabbitAdmin.client.RabbitManagementClient;
import com.example.AndesStayRabbitAdmin.service.RabbitResourceScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "TEST_RABBIT_ADMIN", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealRabbitAdminIntegrationTests {
    private static final String API_PREFIX = "/api/v1/rabbitmq";
    @DynamicPropertySource static void localBroker(DynamicPropertyRegistry registry) {
        String port = System.getenv().getOrDefault("TEST_RABBITMQ_MANAGEMENT_PORT", "15672");
        if (!port.matches("[0-9]{1,5}")) throw new IllegalArgumentException("Puerto local inválido");
        registry.add("andesstay.rabbitmq.management-url", () -> "http://localhost:" + port);
        registry.add("spring.rabbitmq.host", () -> "localhost");
        registry.add("spring.rabbitmq.port", () -> System.getenv().getOrDefault("TEST_RABBITMQ_PORT", "5672"));
        registry.add("spring.rabbitmq.username", () -> System.getenv("RABBITMQ_USERNAME"));
        registry.add("spring.rabbitmq.password", () -> System.getenv("RABBITMQ_PASSWORD"));
    }
    @LocalServerPort int port;
    @Autowired RabbitManagementClient broker;
    @Autowired RabbitResourceScope scope;
    @Autowired RabbitTemplate rabbit;
    @Autowired ConnectionFactory connections;
    @MockitoBean JwtDecoder decoder;
    RestClient api;

    @BeforeEach void authenticateTestClient() {
        for (String role : List.of("Admin", "Huesped"))
            when(decoder.decode(role)).thenReturn(Jwt.withTokenValue(role).header("alg", "RS256")
                    .subject("verification").claim("roles", List.of(role)).build());
        api = RestClient.builder().baseUrl("http://localhost:" + port + API_PREFIX)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer Admin").build();
    }

    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> call(HttpMethod method, String path, Object body) {
        var request = api.method(method).uri(java.net.URI.create("http://localhost:" + port + API_PREFIX + path));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
        return request.retrieve().toEntity(Map.class);
    }
    void expectFailure(int status, HttpMethod method, String path, Object body) {
        var failure = assertThrows(RestClientResponseException.class, () -> call(method, path, body));
        assertEquals(status, failure.getStatusCode().value());
    }
    String bindingPath(String x, String q, String key) {
        return "/bindings/" + x + "/" + q + "?routingKey=" + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);
    }
    Map<String, Object> settings(int ttl, int length) { return Map.of("messageTtlMs", ttl, "maxLength", length); }

    @Test void completeCrudAuthorizationPoliciesAndRoutingOnTheRealBroker() throws Exception {
        String base = "andesstay.ops.verify." + UUID.randomUUID().toString().replace("-", "");
        String x = base + ".topic", alternate = base + ".fallback", q = base + ".queue", fallbackQ = base + ".fallback-queue";
        var coreBefore = broker.queue(scope.reservationQueue());
        var protectedPolicyNamesBefore = broker.policies().stream().map(policy -> policy.name())
                .filter(name -> !name.startsWith(scope.prefix())).sorted().toList();
        try {
            var anonymous = RestClient.create("http://localhost:" + port);
            var unauthorized = assertThrows(RestClientResponseException.class,
                    () -> anonymous.get().uri(API_PREFIX + "/queues").retrieve().toBodilessEntity());
            assertEquals(401, unauthorized.getStatusCode().value());
            var guest = RestClient.builder().baseUrl("http://localhost:" + port)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer Huesped").build();
            assertEquals(403, assertThrows(RestClientResponseException.class,
                    () -> guest.get().uri(API_PREFIX + "/queues").retrieve().toBodilessEntity()).getStatusCode().value());
            expectFailure(403, HttpMethod.DELETE, "/queues/" + scope.reservationQueue(), null);
            expectFailure(400, HttpMethod.POST, "/queues", Map.of("name", q, "settings", settings(0, 10)));
            assertEquals(Boolean.TRUE, call(HttpMethod.GET, "/status", null).getBody().get("connected"));

            for (var entry : Map.of(x, "topic", alternate, "fanout").entrySet()) {
                var response = call(HttpMethod.POST, "/exchanges", Map.of("name", entry.getKey(), "type", entry.getValue()));
                assertEquals(201, response.getStatusCode().value());
                assertEquals(API_PREFIX + "/exchanges/" + entry.getKey(), response.getHeaders().getLocation().toString());
                assertEquals(entry.getKey(), call(HttpMethod.GET, "/exchanges/" + entry.getKey(), null).getBody().get("name"));
            }
            for (String queue : List.of(q, fallbackQ)) {
                var response = call(HttpMethod.POST, "/queues", Map.of("name", queue, "settings", settings(60000, 10)));
                assertEquals(201, response.getStatusCode().value());
                assertEquals(API_PREFIX + "/queues/" + queue, response.getHeaders().getLocation().toString());
                assertEquals(queue, call(HttpMethod.GET, "/queues/" + queue, null).getBody().get("name"));
            }
            expectFailure(409, HttpMethod.POST, "/queues", Map.of("name", q, "settings", settings(60000, 10)));
            call(HttpMethod.PUT, "/queues/" + q, settings(90000, 20));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var effective = broker.queue(q).effectivePolicy();
                assertNotNull(effective, "La política efectiva aún no aparece en las estadísticas");
                assertNotNull(effective.get("message-ttl"));
                assertNotNull(effective.get("max-length"));
                assertEquals(90000, ((Number) effective.get("message-ttl")).intValue());
                assertEquals(20, ((Number) effective.get("max-length")).intValue());
            });
            assertNotNull(call(HttpMethod.GET, "/queues?page=1&pageSize=5", null).getBody().get("items"));
            assertNotNull(call(HttpMethod.GET, "/exchanges?page=1&pageSize=5", null).getBody().get("items"));
            assertEquals(q, call(HttpMethod.GET, "/queue/" + q, null).getBody().get("name"));

            String key = "reservation.#";
            var created = call(HttpMethod.POST, "/bindings", Map.of("exchange", x, "queue", q, "routingKey", key));
            assertEquals(201, created.getStatusCode().value());
            assertEquals(API_PREFIX + bindingPath(x, q, key), created.getHeaders().getLocation().toString());
            assertEquals(key, call(HttpMethod.GET, bindingPath(x, q, key), null).getBody().get("routingKey"));
            var listedBindings = api.get().uri(java.net.URI.create("http://localhost:" + port
                    + API_PREFIX + "/bindings?exchange=" + x + "&queue=" + q)).retrieve().body(List.class);
            assertEquals(1, listedBindings.size());
            expectFailure(409, HttpMethod.POST, "/bindings", Map.of("exchange", x, "queue", q, "routingKey", key));
            expectFailure(409, HttpMethod.DELETE, "/exchanges/" + x, null);
            rabbit.convertAndSend(x, "reservation.created", "routing-verification");
            expectFailure(409, HttpMethod.DELETE, "/queues/" + q, null);
            assertNotNull(rabbit.receive(q, 3000));
            assertEquals("booking.*", call(HttpMethod.PUT, bindingPath(x, q, key), Map.of("routingKey", "booking.*")).getBody().get("routingKey"));
            expectFailure(404, HttpMethod.GET, bindingPath(x, q, key), null);
            rabbit.convertAndSend(x, "booking.created", "new-route-verification");
            assertNotNull(rabbit.receive(q, 3000));
            assertEquals(204, call(HttpMethod.DELETE, bindingPath(x, q, "booking.*"), null).getStatusCode().value());

            call(HttpMethod.POST, "/bindings", Map.of("exchange", alternate, "queue", fallbackQ, "routingKey", ""));
            call(HttpMethod.PUT, "/queues/" + q,
                    Map.of("messageTtlMs", 90000, "maxLength", 20, "deadLetterExchange", alternate));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertNotNull(broker.queue(q).effectivePolicy());
                assertEquals(alternate, broker.queue(q).effectivePolicy().get("dead-letter-exchange"));
            });
            expectFailure(409, HttpMethod.DELETE, "/exchanges/" + alternate, null);
            rabbit.convertAndSend("", q, "dlx-verification");
            try (var connection = connections.createConnection(); var channel = connection.createChannel(false)) {
                var delivery = new java.util.concurrent.atomic.AtomicReference<com.rabbitmq.client.GetResponse>();
                await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                    var received = channel.basicGet(q, false);
                    assertNotNull(received);
                    delivery.set(received);
                });
                channel.basicNack(delivery.get().getEnvelope().getDeliveryTag(), false, false);
            }
            var rejected = rabbit.receive(fallbackQ, 3000);
            assertNotNull(rejected);
            assertTrue(rejected.getMessageProperties().getHeaders().containsKey("x-death"));
            call(HttpMethod.PUT, "/queues/" + q, settings(90000, 20));

            call(HttpMethod.PUT, "/exchanges/" + x, Map.of("alternateExchange", alternate));
            expectFailure(409, HttpMethod.DELETE, "/exchanges/" + alternate, null);
            rabbit.convertAndSend(x, "unmatched", "alternate-verification");
            assertNotNull(rabbit.receive(fallbackQ, 3000));
            call(HttpMethod.PUT, "/exchanges/" + x, Map.of());
            assertFalse(call(HttpMethod.GET, "/exchanges/" + x, null).getBody().toString().contains("alternate-exchange"));

            // Un consumidor activo también bloquea el borrado, aunque la cola esté vacía.
            try (var connection = connections.createConnection(); var channel = connection.createChannel(false)) {
                String consumer = channel.basicConsume(q, false, (tag, delivery) -> {}, tag -> {});
                try { expectFailure(409, HttpMethod.DELETE, "/queues/" + q, null); }
                finally { channel.basicCancel(consumer); }
            }

            call(HttpMethod.DELETE, bindingPath(alternate, fallbackQ, ""), null);
            for (String queue : List.of(q, fallbackQ))
                assertEquals(204, call(HttpMethod.DELETE, "/queues/" + queue, null).getStatusCode().value());
            expectFailure(404, HttpMethod.GET, "/queues/" + q, null);
            for (String exchange : List.of(x, alternate))
                assertEquals(204, call(HttpMethod.DELETE, "/exchanges/" + exchange, null).getStatusCode().value());
            assertEquals(coreBefore.durable(), broker.queue(scope.reservationQueue()).durable());
            assertEquals(coreBefore.effectivePolicy(), broker.queue(scope.reservationQueue()).effectivePolicy());
            assertEquals(protectedPolicyNamesBefore, broker.policies().stream().map(policy -> policy.name())
                    .filter(name -> !name.startsWith(scope.prefix())).sorted().toList());
        } finally {
            // Solo estos cuatro nombres aleatorios; se drenan exclusivamente los mensajes de esta prueba.
            for (String queue : List.of(q, fallbackQ)) {
                if (broker.findQueue(queue).isPresent()) {
                    while (rabbit.receive(queue, 100) != null) { }
                    broker.deleteQueue(queue);
                }
                broker.deletePolicyIfPresent(queue + ".queue-policy");
            }
            for (String exchange : List.of(x, alternate)) {
                if (broker.findExchange(exchange).isPresent()) broker.deleteExchange(exchange);
                broker.deletePolicyIfPresent(exchange + ".exchange-policy");
            }
            assertTrue(broker.policies().stream().noneMatch(policy -> policy.name().startsWith(base)));
            assertTrue(broker.findQueue(q).isEmpty());
            assertTrue(broker.findQueue(fallbackQ).isEmpty());
            assertTrue(broker.findExchange(x).isEmpty());
            assertTrue(broker.findExchange(alternate).isEmpty());
        }
    }
}
