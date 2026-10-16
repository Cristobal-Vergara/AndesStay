package com.example.AndesStay.config;

import com.example.AndesStay.entity.OutboxEvent;
import com.example.AndesStay.service.BrokerGateway;
import com.example.AndesStay.service.ReservationEventType;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RabbitTopologyContractTests {
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(RabbitMQConfig.class, RabbitDemoConfig.class, RabbitDeliveryConfig.class)
        .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
        .withPropertyValues("andesstay.messaging.demo-enabled=true");

    @Test void sharedCatalogIsPackagedUnchangedAndPreservesExistingQueues() {
        contexts.run(context -> {
            assertNull(context.getStartupFailure());
            try (var packaged = getClass().getResourceAsStream("/rabbit-topology.properties")) {
                assertNotNull(packaged, "El JAR debe incluir el catalogo compartido");
                assertArrayEquals(Files.readAllBytes(Path.of("../config/rabbit-topology.properties")), packaged.readAllBytes());
            }
            assertEquals("andesstay.reservations", context.getBean("reservationQueue", Queue.class).getName());
            assertEquals("andesstay.guest-notifications", context.getBean("guestNotificationQueue", Queue.class).getName());
            assertEquals("andesstay.reservations.direct", context.getBean(DirectExchange.class).getName());
            assertEquals("andesstay.reservations.dlq", context.getBean("reservationDeadLetterQueue", Queue.class).getName());
            assertEquals("andesstay.reservations.dlx", context.getBean("reservationDeadBinding", Binding.class).getExchange());
            assertEquals("andesstay.delivery-demo", context.getBean("demoQueue", Queue.class).getName());
            assertEquals("andesstay.delivery-demo.dlq", context.getBean("demoDeadLetterQueue", Queue.class).getName());
            assertEquals(60000, context.getBean("demoDeadLetterQueue", Queue.class).getArguments().get("x-message-ttl"));
        });
    }

    @Test void renamedTopologyKeepsProducerBindingsAndGuestAudienceInAgreement() {
        contexts.withPropertyValues(
            "andesstay.rabbitmq.exchange=verify.changed.exchange",
            "andesstay.rabbitmq.reservation-queue=verify.changed.reception",
            "andesstay.rabbitmq.guest-queue=verify.changed.guest",
            "andesstay.rabbitmq.dead-letter-exchange=verify.changed.dlx",
            "andesstay.rabbitmq.dead-letter-queue=verify.changed.dlq",
            "andesstay.rabbitmq.created-routing-key=verify.created",
            "andesstay.rabbitmq.confirmed-routing-key=verify.confirmed",
            "andesstay.rabbitmq.cancelled-routing-key=verify.cancelled",
            "andesstay.rabbitmq.demo-queue=verify.changed.demo",
            "andesstay.rabbitmq.demo-dlx=verify.changed.demo.dlx",
            "andesstay.rabbitmq.demo-dlq=verify.changed.demo.dlq"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            var rabbit = context.getBean(RabbitTemplate.class);
            var exchange = context.getBean(DirectExchange.class);
            @SuppressWarnings("unchecked")
            var keys = (Map<ReservationEventType, String>) context.getBean("reservationRoutingKeys");
            @SuppressWarnings("unchecked")
            KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
            var gateway = new BrokerGateway(rabbit, kafka, 1, exchange, keys);
            var bindings = context.getBean("reservationBindings", Declarables.class).getDeclarables().stream()
                .map(Binding.class::cast).toList();
            doAnswer(invocation -> {
                assertEquals("verify.changed.exchange", invocation.getArgument(0));
                String key = invocation.getArgument(1);
                assertTrue(Set.of("verify.created", "verify.confirmed", "verify.cancelled").contains(key));
                Set<String> recipients = bindings.stream().filter(binding -> binding.getRoutingKey().equals(key))
                    .peek(binding -> assertEquals(exchange.getName(), binding.getExchange()))
                    .map(Binding::getDestination).collect(Collectors.toSet());
                assertEquals(key.equals("verify.created") ? Set.of("verify.changed.reception")
                    : Set.of("verify.changed.reception", "verify.changed.guest"), recipients);
                ((CorrelationData) invocation.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true, null));
                return null;
            }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
            for (var type : ReservationEventType.values()) {
                gateway.publish(new OutboxEvent(UUID.randomUUID().toString(), "RABBITMQ",
                    "{\"type\":\"" + type.name() + "\"}", LocalDateTime.now()));
            }
            verify(rabbit, times(3)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
            var deadBinding = context.getBean("reservationDeadBinding", Binding.class);
            assertEquals("verify.changed.dlx", deadBinding.getExchange());
            assertEquals("verify.changed.dlq", deadBinding.getDestination());
            var demoQueue = context.getBean("demoQueue", Queue.class);
            assertEquals("verify.changed.demo", demoQueue.getName());
            assertEquals("verify.changed.demo.dlx", demoQueue.getArguments().get("x-dead-letter-exchange"));
            assertEquals("verify.changed.demo.dlq", context.getBean("demoDeadBinding", Binding.class).getDestination());
        });
    }
}
