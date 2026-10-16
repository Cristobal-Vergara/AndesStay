package com.example.AndesStay.service;

import com.example.AndesStay.config.RabbitDeliveryConfig;
import com.example.AndesStay.messaging.delivery.RabbitDeliveryHandler;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in comparison of synthetic notification work; never creates reservations or writes to MySQL. */
@EnabledIfEnvironmentVariable(named = "TEST_RABBIT_BENCHMARK", matches = "true")
class RabbitThroughputBrokerTests {
    private static final int WARMUP_MESSAGES = 30;
    private static final int PAYLOAD_BYTES = 256;
    private final RabbitDeliveryHandler delivery = new RabbitDeliveryConfig()
            .rabbitDeliveryHandler(2, 1000, 2, 10000);

    @Test void compareConsumerAndPrefetchSettingsWithoutMissingOrRepeatedWork() throws Exception {
        int messages = option("BENCHMARK_MESSAGES", 300, 30, 3000);
        int repeats = option("BENCHMARK_REPEATS", 3, 1, 10);
        int workMs = option("BENCHMARK_WORK_MS", 5, 1, 25);
        String runId = System.getenv().getOrDefault("BENCHMARK_RUN_ID", UUID.randomUUID().toString());
        String prefix = "andesstay.benchmark." + UUID.randomUUID();
        CachingConnectionFactory connection = new CachingConnectionFactory("localhost",
                option("TEST_RABBITMQ_PORT", 5672, 1, 65535));
        connection.setUsername(System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest"));
        connection.setPassword(System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest"));
        connection.getRabbitConnectionFactory().setConnectionTimeout(5000);
        RabbitAdmin admin = new RabbitAdmin(connection);
        List<Map<String, Object>> results = new ArrayList<>();
        String brokerVersion;
        try {
            brokerVersion = String.valueOf(connection.createConnection().getDelegate().getServerProperties().get("version"));
            // Rotating the case order across rounds reduces a systematic first/last-case bias.
            int[][] cases = {{1, 1}, {1, 10}, {3, 1}, {3, 10}};
            for (int round = 0; round <= repeats; round++) {
                for (int offset = 0; offset < cases.length; offset++) {
                    int[] settings = cases[(offset + round) % cases.length];
                    boolean warmup = round == 0;
                    String queue = prefix + ".r" + round + ".c" + settings[0] + ".p" + settings[1];
                    Map<String, Object> result = runBatch(connection, admin, queue, settings[0], settings[1],
                            warmup ? WARMUP_MESSAGES : messages, workMs, round);
                    if (!warmup) results.add(result);
                }
            }
        } finally {
            connection.destroy();
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("runId", runId);
        report.put("recordedAtUtc", Instant.now().toString());
        report.put("workload", "Synthetic reservation notification; fixed blocking work; no database or HTTP");
        report.put("measurement", "Gate release to last local ACK submission; excludes publishing, startup and cleanup");
        report.put("brokerVersion", brokerVersion);
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name"));
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.put("payloadBytes", PAYLOAD_BYTES);
        report.put("workMs", workMs);
        report.put("messagesPerTrial", messages);
        report.put("warmupMessagesPerCase", WARMUP_MESSAGES);
        report.put("repeatsPerCase", repeats);
        report.put("ackMode", "MANUAL");
        report.put("publishPersistent", true);
        report.put("publisherConfirms", true);
        report.put("allTemporaryQueuesDeleted", true);
        report.put("trials", results);
        report.put("summaries", summaries(results));
        Path output = Path.of("..", ".validation", "rabbit-benchmark.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, JsonMapper.builder().build().writerWithDefaultPrettyPrinter()
                .writeValueAsString(report), StandardCharsets.UTF_8);
    }

    private Map<String, Object> runBatch(CachingConnectionFactory connection, RabbitAdmin admin, String queue,
            int consumers, int prefetch, int messages, int workMs, int round) throws Exception {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connection);
        container.setQueueNames(queue);
        container.setConcurrentConsumers(consumers);
        container.setMaxConcurrentConsumers(consumers);
        container.setPrefetchCount(prefetch);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setDefaultRequeueRejected(false);
        container.setAutoStartup(false);
        container.setAutoDeclare(false);
        container.setReceiveTimeout(100);
        container.setShutdownTimeout(5000);
        Batch batch = new Batch(messages, consumers, workMs);
        container.setMessageListener((ChannelAwareMessageListener) batch::receive);
        try {
            // No bindings to production exchanges. Expiry is a fallback if the test process is killed.
            admin.declareQueue(new Queue(queue, true, false, false,
                    Map.of("x-expires", 600000, "x-message-ttl", 300000)));
            assertEquals(0, admin.getQueueInfo(queue).getConsumerCount());
            publish(connection, queue, batch.ids);
            assertEquals(messages, admin.getQueueInfo(queue).getMessageCount(), "The paused queue must hold the full batch");
            container.afterPropertiesSet();
            container.start();
            await().atMost(Duration.ofSeconds(10)).until(() -> admin.getQueueInfo(queue).getConsumerCount() == consumers);
            assertTrue(batch.entered.await(10, TimeUnit.SECONDS), "All consumers must reach the starting gate");
            batch.startNanos.set(System.nanoTime());
            batch.gate.countDown();
            assertTrue(batch.done.await(batch.timeoutSeconds, TimeUnit.SECONDS), "Batch did not finish within its time budget");
            assertTrue(batch.failures.isEmpty(), () -> "Consumer failures: " + batch.failures);
            assertEquals(batch.ids, batch.effects.keySet(), "Missing or unexpected message IDs");
            assertTrue(batch.effects.values().stream().allMatch(count -> count == 1), "Repeated processing detected");
            assertEquals(messages, batch.completionNanos.size(), "Every effect must be followed by a local ACK submission");
            container.stop();
            // A stopped consumer requeues an unacknowledged delivery; zero ready messages now also checks ACK drain.
            assertEquals(0, admin.getQueueInfo(queue).getConsumerCount());
            assertEquals(0, admin.getQueueInfo(queue).getMessageCount(), "Pending or requeued messages after stop");
            List<Long> durations = batch.completionNanos.values().stream().sorted().toList();
            List<Long> workDurations = batch.workNanos.values().stream().sorted().toList();
            double elapsedMs = durations.get(durations.size() - 1) / 1_000_000.0;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("round", round);
            result.put("consumers", consumers);
            result.put("prefetch", prefetch);
            result.put("published", messages);
            result.put("pausedReady", messages);
            result.put("uniqueProcessed", batch.effects.size());
            result.put("duplicateEffects", 0);
            result.put("localAckSubmissions", batch.completionNanos.size());
            result.put("readyAfterStop", 0);
            result.put("elapsedMs", elapsedMs);
            result.put("messagesPerSecond", messages * 1000.0 / elapsedMs);
            result.put("completionP50Ms", percentileNanos(durations, 0.5) / 1_000_000.0);
            result.put("completionP95Ms", percentileNanos(durations, 0.95) / 1_000_000.0);
            result.put("observedWorkP50Ms", percentileNanos(workDurations, 0.5) / 1_000_000.0);
            result.put("observedWorkP95Ms", percentileNanos(workDurations, 0.95) / 1_000_000.0);
            return result;
        } finally {
            batch.gate.countDown();
            try {
                container.stop();
                container.destroy();
            } finally {
                admin.deleteQueue(queue);
                assertNull(admin.getQueueInfo(queue), "Temporary queue was not deleted");
            }
        }
    }

    private void publish(CachingConnectionFactory connection, String queue, Set<String> ids) throws Exception {
        AtomicInteger returns = new AtomicInteger();
        try (Channel channel = connection.createConnection().createChannel(false)) {
            channel.confirmSelect();
            channel.addReturnListener(returned -> returns.incrementAndGet());
            for (String id : ids.stream().sorted().toList()) {
                var properties = new AMQP.BasicProperties.Builder().messageId(id).deliveryMode(2)
                        .contentType("application/octet-stream").build();
                byte[] body = Arrays.copyOf(("reservation.notification.test:" + id).getBytes(StandardCharsets.UTF_8), PAYLOAD_BYTES);
                channel.basicPublish("", queue, true, properties, body);
            }
            channel.waitForConfirmsOrDie(10000);
            assertEquals(0, returns.get(), "A publication had no route to the temporary queue");
        }
    }

    private final class Batch {
        private final Set<String> ids = ConcurrentHashMap.newKeySet();
        private final Map<String, Integer> effects = new ConcurrentHashMap<>();
        private final Map<String, Long> completionNanos = new ConcurrentHashMap<>();
        private final Map<String, Long> workNanos = new ConcurrentHashMap<>();
        private final List<String> failures = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch gate = new CountDownLatch(1);
        private final CountDownLatch entered;
        private final CountDownLatch done;
        private final AtomicLong startNanos = new AtomicLong();
        private final int workMs;
        private final long timeoutSeconds;

        private Batch(int messages, int consumers, int workMs) {
            this.workMs = workMs;
            this.timeoutSeconds = Math.max(15, messages * (long) workMs * 5 / 1000 + 10);
            this.entered = new CountDownLatch(consumers);
            this.done = new CountDownLatch(messages);
            for (int i = 0; i < messages; i++) ids.add(String.format(Locale.ROOT, "notice-%04d", i));
        }

        private void receive(Message message, Channel channel) throws Exception {
            try {
                entered.countDown();
                if (!gate.await(timeoutSeconds, TimeUnit.SECONDS)) throw new IllegalStateException("Starting gate timed out");
                String id = message.getMessageProperties().getMessageId();
                AtomicBoolean processed = new AtomicBoolean();
                delivery.handle(message, channel, () -> {
                    if (id == null || !ids.contains(id) || message.getBody().length != PAYLOAD_BYTES)
                        throw new IllegalStateException("Unexpected synthetic message");
                    long workStarted = System.nanoTime();
                    try { Thread.sleep(workMs); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Synthetic work interrupted", interrupted);
                    }
                    workNanos.put(id, System.nanoTime() - workStarted);
                    effects.merge(id, 1, Integer::sum);
                    processed.set(true);
                });
                if (!processed.get()) throw new IllegalStateException("Delivery rejected instead of processed");
                completionNanos.put(id, System.nanoTime() - startNanos.get());
            } catch (Exception failure) {
                failures.add(failure.getClass().getSimpleName());
                throw failure;
            } finally {
                done.countDown();
            }
        }
    }

    private List<Map<String, Object>> summaries(List<Map<String, Object>> trials) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int consumers : List.of(1, 3)) {
            for (int prefetch : List.of(1, 10)) {
                List<Map<String, Object>> matches = trials.stream().filter(trial ->
                        trial.get("consumers").equals(consumers) && trial.get("prefetch").equals(prefetch)).toList();
                List<Double> rates = matches.stream().map(trial -> (Double) trial.get("messagesPerSecond")).sorted().toList();
                List<Double> times = matches.stream().map(trial -> (Double) trial.get("elapsedMs")).sorted().toList();
                result.add(Map.of("consumers", consumers, "prefetch", prefetch,
                        "medianElapsedMs", median(times), "medianMessagesPerSecond", median(rates),
                        "minMessagesPerSecond", rates.get(0), "maxMessagesPerSecond", rates.get(rates.size() - 1)));
            }
        }
        return result;
    }

    private static double median(List<Double> sorted) {
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2 : sorted.get(mid);
    }

    private static long percentileNanos(List<Long> sorted, double percentile) {
        return sorted.get((int) Math.ceil(sorted.size() * percentile) - 1);
    }

    private static int option(String name, int fallback, int min, int max) {
        int value = Integer.parseInt(System.getenv().getOrDefault(name, Integer.toString(fallback)));
        if (value < min || value > max) throw new IllegalArgumentException("Out of range: " + name);
        return value;
    }
}
