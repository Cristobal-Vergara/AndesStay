package com.example.AndesStay.messaging.notifications;

import com.example.AndesStay.service.InboxService;
import com.example.AndesStay.service.ReservationNotificationService;
import com.example.AndesStay.messaging.delivery.RabbitDeliveryHandler;
import com.example.AndesStay.messaging.RabbitListenerIds;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.io.IOException;

@Component
public class ReceptionNotificationConsumer {
    private final InboxService inbox;
    private final ReservationNotificationService notifications;
    private final RabbitDeliveryHandler delivery;

    public ReceptionNotificationConsumer(InboxService inbox, ReservationNotificationService notifications,
            RabbitDeliveryHandler delivery) {
        this.inbox = inbox;
        this.notifications = notifications;
        this.delivery = delivery;
    }

    @RabbitListener(id = RabbitListenerIds.RECEPTION, queues = "#{reservationQueue.name}", ackMode = "MANUAL")
    public void receive(Message message, Channel channel) throws IOException {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        delivery.handle(message, channel,
                () -> inbox.process("RABBITMQ", payload, () -> notifications.receive(payload, false)));
    }
}
