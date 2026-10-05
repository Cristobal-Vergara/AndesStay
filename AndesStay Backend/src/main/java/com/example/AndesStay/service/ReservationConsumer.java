package com.example.AndesStay.service;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

@Service
public class ReservationConsumer {
    private final InboxService inbox;
    private final ReservationNotificationService notifications;
    public ReservationConsumer(InboxService inbox, ReservationNotificationService notifications) {
        this.inbox = inbox; this.notifications = notifications;
    }

    @RabbitListener(queues = "#{reservationQueue.name}")
    public void receiveReservation(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        inbox.process("RABBITMQ", payload, () -> notifications.receive(payload, false));
    }

    @RabbitListener(queues = "#{guestNotificationQueue.name}")
    public void receiveGuestNotification(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        // Otra cola representa otro efecto; su deduplicación debe ser independiente.
        inbox.process("RABBITMQ_GUEST", payload, () -> notifications.receive(payload, true));
    }
}
