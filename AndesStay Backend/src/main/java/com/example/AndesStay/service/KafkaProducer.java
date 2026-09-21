package com.example.AndesStay.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducer {

    private static final String TOPIC = "andesstay.events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendReservationEvent(String message) {

        kafkaTemplate.send(TOPIC, message);

        System.out.println("=================================");
        System.out.println("EVENTO ENVIADO A KAFKA");
        System.out.println(message);
        System.out.println("=================================");
    }
}