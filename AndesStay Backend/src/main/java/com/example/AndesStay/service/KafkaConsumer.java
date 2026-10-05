package com.example.AndesStay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);
    private final InboxService inbox;
    public KafkaConsumer(InboxService inbox) { this.inbox = inbox; }

    @KafkaListener(topics = "andesstay.events", groupId = "${spring.kafka.consumer.group-id:andesstay-group}")
    public void receiveEvent(String payload) {
        inbox.process("KAFKA", payload, () -> log.info("Evento Kafka registrado"));
    }
}
