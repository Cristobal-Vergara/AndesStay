package com.example.AndesStay.service;

import org.springframework.stereotype.Service;

@Service
public class KafkaProducer {
    private final OutboxService outbox;
    public KafkaProducer(OutboxService outbox) { this.outbox = outbox; }

    public void sendReservationEvent(String message) {
        outbox.enqueue("KAFKA", message.startsWith("AUDITORIA:") ? "AUDIT_EVENT" : "RESERVATION_CREATED", message);
    }
}
