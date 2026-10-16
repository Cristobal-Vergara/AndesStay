package com.example.AndesStay.service;

import com.example.AndesStay.config.RabbitDeliveryConfig;
import com.example.AndesStay.messaging.notifications.ReceptionNotificationConsumer;
import com.example.AndesStay.repository.ReservationNotificationRepository;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory.AddressShuffleMode;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_RABBITMQ_CLUSTER", matches = "true")
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:cluster_recovery;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({InboxService.class, ReservationAccess.class, ReservationNotificationService.class,
    ReceptionNotificationConsumer.class, RabbitDeliveryConfig.class, RabbitClusterRecoveryTests.TestClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RabbitClusterRecoveryTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String project = System.getenv("CLUSTER_PROJECT");
    private final String runId = System.getenv("CLUSTER_RUN_ID");
    private final JsonNode endpoints = JSON.readTree(System.getenv("CLUSTER_ENDPOINTS_JSON"));
    private final String username = System.getenv("CLUSTER_USERNAME");
    private final String password = System.getenv("CLUSTER_PASSWORD");
    private final Set<String> stopped = new HashSet<>();
    @Autowired ReceptionNotificationConsumer consumer;
    @Autowired ReservationNotificationRepository notices;
    @Autowired JdbcTemplate jdbc;
    @Value("${andesstay.rabbitmq.created-routing-key}") String routingKey;

    @TestConfiguration static class TestClock {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneId.of("America/Santiago")); }
    }

    @Test void quorumFailoverRedeliversCommittedNoticeAndRecoversAfterLosingMajority() throws Exception {
        assertNotNull(project);
        assertTrue(project.matches("andesstay-cluster-test-[a-f0-9]{8}"));
        String queue = "andesstay.cluster-test." + runId + ".reception";
        String exchange = "andesstay.cluster-test." + runId + ".direct";
        var events = new ArrayList<String>();
        var deliveries = new CopyOnWriteArrayList<Message>();
        var listenerErrors = new CopyOnWriteArrayList<Exception>();
        CachingConnectionFactory seed = connections(List.of("rabbit1", "rabbit2", "rabbit3"));
        CachingConnectionFactory resilient = null;
        SimpleMessageListenerContainer listener = null;
        var report = new LinkedHashMap<String, Object>();
        report.put("runId", runId);
        try {
            await().atMost(Duration.ofSeconds(30)).ignoreExceptions().until(() -> running("rabbit1") == 3);
            RabbitAdmin admin = new RabbitAdmin(seed);
            admin.declareExchange(new DirectExchange(exchange, true, false));
            admin.declareQueue(new org.springframework.amqp.core.Queue(queue, true, false, false,
                Map.of("x-queue-type", "quorum", "x-quorum-initial-group-size", 3)));
            admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, null));
            await().atMost(Duration.ofSeconds(20)).ignoreExceptions().until(() -> queue("rabbit1", queue).path("online").size() == 3);
            assertEquals(3, queue("rabbit1", queue).path("members").size());
            report.put("replicas", 3);
            RabbitTemplate initial = publisher(seed);
            for (int i = 0; i < 10; i++) { String payload = payload(i + 1); events.add(payload); confirmed(initial, exchange, payload); }

            String leaderBefore = queue("rabbit1", queue).path("leader").asString();
            String leaderService = service(leaderBefore);
            var order = new ArrayList<>(List.of("rabbit1", "rabbit2", "rabbit3"));
            order.remove(leaderService); order.add(0, leaderService);
            resilient = connections(order);
            assertEquals(port(leaderService, "amqp"), resilient.createConnection().getDelegate().getPort());
            String survivor = order.get(1);

            ConnectionFactory nativeFactory = new ConnectionFactory();
            nativeFactory.setHost("127.0.0.1"); nativeFactory.setPort(port(leaderService, "amqp"));
            nativeFactory.setUsername(username); nativeFactory.setPassword(password);
            nativeFactory.setAutomaticRecoveryEnabled(false); nativeFactory.setRequestedHeartbeat(2);
            nativeFactory.setConnectionTimeout(5000);
            long failureStarted;
            String firstId;
            var rawConnection = nativeFactory.newConnection();
            var held = rawConnection.createChannel();
            try {
                var first = held.basicGet(queue, false);
                assertNotNull(first);
                firstId = JSON.readTree(first.getBody()).path("id").asString();
                Message delivery = message(first, queue);
                // Delega en el canal real. Solo intercala la caida antes del ACK;
                // la excepcion de ACK procede del canal cerrado por el broker caido.
                Channel failBeforeAck = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[]{Channel.class}, (proxy, method, args) -> {
                    if (method.getName().equals("basicAck")) {
                        assertEquals(1, notices.count(), "El aviso debe estar confirmado antes del ACK");
                        assertEquals(1, receipts());
                        stop(leaderService);
                        await().atMost(Duration.ofSeconds(10)).until(() -> !held.isOpen());
                    }
                    try { return method.invoke(held, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
                failureStarted = System.nanoTime();
                assertThrows(AlreadyClosedException.class, () -> consumer.receive(delivery, failBeforeAck));
            } finally {
                if (held.isOpen()) held.abort();
                if (rawConnection.isOpen()) rawConnection.abort();
            }
            await().atMost(Duration.ofSeconds(20)).ignoreExceptions().until(() -> {
                var state = queue(survivor, queue);
                return state.path("online").size() == 2 && !leaderBefore.equals(state.path("leader").asString());
            });
            String leaderAfter = queue(survivor, queue).path("leader").asString();
            report.put("leaderBefore", leaderBefore); report.put("leaderAfter", leaderAfter);
            report.put("leaderElectionObservedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - failureStarted));
            assertEquals(2, running(survivor));
            CachingConnectionFactory recovered = resilient;
            await().atMost(Duration.ofSeconds(15)).ignoreExceptions().until(() -> recovered.createConnection().getDelegate().getPort() != port(leaderService, "amqp"));
            report.put("clientReconnectedToAnotherNode", true);

            RabbitTemplate publishing = publisher(resilient);
            for (int i = 10; i < 20; i++) { String payload = payload(i + 1); events.add(payload); confirmed(publishing, exchange, payload); }
            listener = new SimpleMessageListenerContainer(resilient);
            listener.setQueueNames(queue); listener.setAcknowledgeMode(AcknowledgeMode.MANUAL); listener.setPrefetchCount(1);
            listener.setMessageListener((ChannelAwareMessageListener) (message, channel) -> {
                try { consumer.receive(message, channel); deliveries.add(message); }
                catch (Exception failure) { listenerErrors.add(failure); throw failure; }
            });
            listener.start();
            await().atMost(Duration.ofSeconds(30)).until(() -> notices.count() == 20 && deliveries.size() >= 20);
            assertTrue(listenerErrors.isEmpty());
            assertEquals(20, receipts());
            assertTrue(deliveries.stream().anyMatch(message -> firstId.equals(JSON.readTree(message.getBody()).path("id").asString())
                && Boolean.TRUE.equals(message.getMessageProperties().isRedelivered())), "El mensaje sin ACK debe ser reentregado");
            Set<String> deliveredIds = new HashSet<>();
            deliveries.forEach(message -> deliveredIds.add(JSON.readTree(message.getBody()).path("id").asString()));
            assertEquals(20, deliveredIds.size());
            for (String payload : events) assertTrue(deliveredIds.contains(JSON.readTree(payload).path("id").asString()));
            report.put("committedEventRedelivered", true);
            report.put("noticesAfterLeaderFailure", notices.count());
            listener.stop();
            start(leaderService);
            await().atMost(Duration.ofSeconds(40)).ignoreExceptions().until(() -> queue(survivor, queue).path("online").size() == 3 && running(survivor) == 3);

            // Sin dos replicas no hay mayoria: una publicacion no puede darse por segura.
            String onlySurvivor = service(queue(survivor, queue).path("leader").asString());
            var offline = new ArrayList<>(List.of("rabbit1", "rabbit2", "rabbit3")); offline.remove(onlySurvivor);
            for (String node : offline) stop(node);
            await().atMost(Duration.ofSeconds(20)).ignoreExceptions().until(() -> running(onlySurvivor) == 1);
            CachingConnectionFactory single = connections(List.of(onlySurvivor));
            try {
                RabbitTemplate isolatedPublisher = publisher(single);
                String uncertain = payload(21);
                CorrelationData pending = send(isolatedPublisher, exchange, uncertain);
                assertThrows(TimeoutException.class, () -> pending.getFuture().get(2, TimeUnit.SECONDS));
                report.put("noConfirmWithoutMajority", true);
                for (String node : offline) start(node);
                await().atMost(Duration.ofSeconds(40)).ignoreExceptions().until(() -> queue(onlySurvivor, queue).path("online").size() == 3 && running(onlySurvivor) == 3);
                // El resultado de la primera publicacion era incierto. Reenviar el mismo ID
                // es seguro para el efecto gracias al inbox, aunque existan dos entregas.
                confirmed(isolatedPublisher, exchange, uncertain);
                listener.start();
                await().atMost(Duration.ofSeconds(30)).until(() -> notices.count() == 21 && receipts() == 21);
                await().atMost(Duration.ofSeconds(20)).ignoreExceptions().until(() -> queue(onlySurvivor, queue).path("messages").asInt(-1) == 0);
                assertTrue(listenerErrors.isEmpty());
                assertEquals(21, notices.count());
                report.put("majorityRecovered", true);
            } finally { single.destroy(); }
            report.put("uniqueEvents", 21); report.put("notices", notices.count()); report.put("inboxReceipts", receipts());
            report.put("success", true);
            Files.writeString(Path.of(System.getenv("CLUSTER_TEST_REPORT")), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        } finally {
            if (listener != null) listener.stop();
            for (String node : new ArrayList<>(stopped)) start(node);
            if (resilient != null) resilient.destroy();
            seed.destroy();
        }
    }

    private CachingConnectionFactory connections(List<String> services) {
        var factory = new CachingConnectionFactory();
        factory.setAddresses(String.join(",", services.stream().map(s -> "127.0.0.1:" + port(s, "amqp")).toList()));
        factory.setAddressShuffleMode(AddressShuffleMode.NONE);
        factory.setUsername(username); factory.setPassword(password);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.getRabbitConnectionFactory().setConnectionTimeout(3000);
        factory.getRabbitConnectionFactory().setRequestedHeartbeat(2);
        return factory;
    }
    private RabbitTemplate publisher(CachingConnectionFactory connections) {
        var template = new RabbitTemplate(connections); template.setMandatory(true); return template;
    }
    private CorrelationData send(RabbitTemplate template, String exchange, String payload) {
        var properties = new MessageProperties(); properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        var correlation = new CorrelationData(UUID.randomUUID().toString());
        template.send(exchange, routingKey, new Message(payload.getBytes(StandardCharsets.UTF_8), properties), correlation);
        return correlation;
    }
    private void confirmed(RabbitTemplate template, String exchange, String payload) throws Exception {
        var correlation = send(template, exchange, payload);
        assertTrue(correlation.getFuture().get(15, TimeUnit.SECONDS).ack()); assertNull(correlation.getReturned());
    }
    private String payload(long reservation) {
        return "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"RESERVATION_CREATED\",\"data\":{\"reservationId\":" + reservation + ",\"unitId\":1}}";
    }
    private Message message(com.rabbitmq.client.GetResponse response, String queue) {
        var properties = new MessageProperties();
        properties.setConsumerQueue(queue); properties.setDeliveryTag(response.getEnvelope().getDeliveryTag());
        properties.setRedelivered(response.getEnvelope().isRedeliver());
        return new Message(response.getBody(), properties);
    }
    private long receipts() { return jdbc.queryForObject("select count(*) from consumed_events where destination='RABBITMQ'", Long.class); }
    private int port(String service, String kind) { return endpoints.path(service).path(kind).asInt(); }
    private String service(String node) { String name = node.substring(node.indexOf('@') + 1); assertTrue(Set.of("rabbit1", "rabbit2", "rabbit3").contains(name)); return name; }
    private JsonNode queue(String service, String name) throws Exception { return api(service, "/queues/%2F/" + name); }
    private int running(String service) throws Exception {
        int total = 0; for (var node : api(service, "/nodes")) if (node.path("running").asBoolean()) total++; return total;
    }
    private JsonNode api(String service, String path) throws Exception {
        String authorization = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port(service, "management") + "/api" + path))
            .timeout(Duration.ofSeconds(5)).header("Authorization", "Basic " + authorization).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString()); assertEquals(200, response.statusCode()); return JSON.readTree(response.body());
    }
    private void stop(String service) throws Exception { dockerNode("kill", service); stopped.add(service); }
    private void start(String service) throws Exception { dockerNode("start", service); stopped.remove(service); }
    private void dockerNode(String action, String service) throws Exception {
        String id = endpoints.path(service).path("containerId").asString();
        assertTrue(id.matches("[a-f0-9]{64}"));
        var inspect = new ProcessBuilder("docker", "inspect", "--format", "{{json .Config.Labels}}", id).redirectErrorStream(true).start();
        if (!inspect.waitFor(10, TimeUnit.SECONDS)) { inspect.destroyForcibly(); fail("Docker no respondio al verificar el nodo temporal"); }
        String labelsText = new String(inspect.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, inspect.exitValue(), labelsText);
        var labels = JSON.readTree(labelsText);
        assertEquals(project, labels.path("com.docker.compose.project").asString());
        assertEquals(service, labels.path("com.docker.compose.service").asString());
        var command = new ProcessBuilder("docker", action, id).redirectErrorStream(true).start();
        if (!command.waitFor(15, TimeUnit.SECONDS)) { command.destroyForcibly(); fail("Docker no respondio al controlar el nodo temporal"); }
        assertEquals(0, command.exitValue(), "Fallo al controlar un nodo del proyecto temporal");
    }
}
