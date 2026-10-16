package com.example.AndesStay.service;

import com.example.AndesStay.dto.ListenerStatusResponse.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.example.AndesStay.messaging.RabbitListenerIds.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_RABBITMQ", matches = "true")
class ListenerControlBrokerTests {
    @Test void gracefulPausePreservesUnackedAndQueuedMessagesAndKeepsOtherAudienceRunning() throws Exception {
        String prefix = "andesstay.listener-test." + UUID.randomUUID();
        String receptionQueue = prefix + ".reception";
        String guestQueue = prefix + ".guest";
        int port = Integer.parseInt(System.getenv().getOrDefault("TEST_RABBITMQ_PORT", "5672"));
        CachingConnectionFactory connection = new CachingConnectionFactory("localhost", port);
        connection.setUsername(System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest"));
        connection.setPassword(System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest"));
        connection.getRabbitConnectionFactory().setConnectionTimeout(5000);
        RabbitAdmin admin = new RabbitAdmin(connection);
        RabbitListenerEndpointRegistry registry = new RabbitListenerEndpointRegistry();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Set<String> received = ConcurrentHashMap.newKeySet();
        try {
            Map<String, Object> retention = Map.of("x-expires", 300000, "x-message-ttl", 120000);
            admin.declareQueue(new Queue(receptionQueue, true, false, false, retention));
            admin.declareQueue(new Queue(guestQueue, true, false, false, retention));
            SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
            factory.setConnectionFactory(connection);
            factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            factory.setPrefetchCount(1);
            register(registry, factory, RECEPTION, receptionQueue, (message, channel) -> {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                if (payload.equals("held")) {
                    started.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out waiting for release");
                }
                received.add(payload);
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            });
            register(registry, factory, GUEST, guestQueue, (message, channel) -> {
                received.add(new String(message.getBody(), StandardCharsets.UTF_8));
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            });
            registry.start();
            ListenerControlService service = new ListenerControlService(registry);
            RabbitTemplate template = new RabbitTemplate(connection);
            await().atMost(Duration.ofSeconds(10)).until(() -> admin.getQueueInfo(receptionQueue).getConsumerCount() == 1);
            template.convertAndSend("", receptionQueue, "held");
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(State.STOPPING, service.pause(RECEPTION).state());
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> service.resume(RECEPTION)).getStatusCode());
            template.convertAndSend("", receptionQueue, "queued");
            template.convertAndSend("", guestQueue, "guest-during-pause");
            await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("guest-during-pause"));
            assertFalse(received.contains("held"));
            release.countDown();
            await().atMost(Duration.ofSeconds(10)).until(() -> service.status(RECEPTION).state() == State.STOPPED);
            await().atMost(Duration.ofSeconds(5)).until(() -> admin.getQueueInfo(receptionQueue).getConsumerCount() == 0);
            assertTrue(received.contains("held"));
            assertFalse(received.contains("queued"));
            assertEquals(1, admin.getQueueInfo(receptionQueue).getMessageCount());
            assertEquals(1, admin.getQueueInfo(guestQueue).getConsumerCount());
            assertEquals(State.STOPPED, service.pause(RECEPTION).state());
            assertEquals(State.RUNNING, service.resume(RECEPTION).state());
            assertEquals(State.RUNNING, service.resume(RECEPTION).state());
            await().atMost(Duration.ofSeconds(10)).until(() -> received.contains("queued"));
            await().atMost(Duration.ofSeconds(5)).until(() -> admin.getQueueInfo(receptionQueue).getMessageCount() == 0);
            assertEquals(1, admin.getQueueInfo(receptionQueue).getConsumerCount());
        } finally {
            release.countDown();
            registry.stop();
            registry.destroy();
            try {
                admin.deleteQueue(receptionQueue);
                admin.deleteQueue(guestQueue);
                assertNull(admin.getQueueInfo(receptionQueue));
                assertNull(admin.getQueueInfo(guestQueue));
            } finally { connection.destroy(); }
        }
    }

    private void register(RabbitListenerEndpointRegistry registry, SimpleRabbitListenerContainerFactory factory,
            String id, String queue, ChannelAwareMessageListener listener) {
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId(id);
        endpoint.setQueueNames(queue);
        endpoint.setMessageListener(listener);
        registry.registerListenerContainer(endpoint, factory);
    }
}
