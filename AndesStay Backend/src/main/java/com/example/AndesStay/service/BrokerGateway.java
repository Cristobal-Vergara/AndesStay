package com.example.AndesStay.service;

import org.springframework.amqp.core.DirectExchange;
import com.example.AndesStay.entity.OutboxEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class BrokerGateway {
    private final RabbitTemplate rabbit;
    private final KafkaTemplate<String, String> kafka;
    private final long timeoutSeconds;
    private final DirectExchange exchange;

    public BrokerGateway(RabbitTemplate rabbit, KafkaTemplate<String, String> kafka,
            @Value("${andesstay.outbox.confirm-timeout-seconds:10}") long timeoutSeconds,
            DirectExchange reservationExchange) {
        this.rabbit = rabbit; this.kafka = kafka; this.timeoutSeconds = timeoutSeconds;
        this.exchange = reservationExchange;
    }

    public void publish(OutboxEvent event) throws Exception {
        if (event.getDestination().equals("KAFKA")) {
            kafka.send("andesstay.events", event.getId(), event.getPayload())
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            return;
        }
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.getId());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        CorrelationData correlation = new CorrelationData(event.getId());
        rabbit.send(exchange.getName(), ReservationEventType.fromPayload(event.getPayload()).routingKey(),
                new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties), correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(timeoutSeconds, TimeUnit.SECONDS);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new IllegalStateException("El broker no confirmó el mensaje o no existe su destino");
        }
    }
}
