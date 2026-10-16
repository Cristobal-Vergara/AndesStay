package com.example.AndesStay.messaging.demo;

import com.example.AndesStay.messaging.delivery.RabbitDeliveryHandler;
import com.example.AndesStay.messaging.RabbitListenerIds;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import java.io.IOException;

public class DeliveryDemoConsumer {
    private final RabbitDeliveryHandler delivery;

    public DeliveryDemoConsumer(RabbitDeliveryHandler delivery) {
        this.delivery = delivery;
    }

    @RabbitListener(id = RabbitListenerIds.DEMO, queues = "#{demoQueue.name}", ackMode = "MANUAL")
    public void receive(Message message, Channel channel) throws IOException {
        delivery.handle(message, channel, this::simulateFailure);
    }

    public void simulateFailure() {
        // Fallo determinista, exclusivamente en la cola de demostración.
        throw new IllegalStateException("Prueba controlada de entrega");
    }
}
