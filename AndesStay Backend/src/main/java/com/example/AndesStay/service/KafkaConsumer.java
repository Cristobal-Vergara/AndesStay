package com.example.AndesStay.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {

    @KafkaListener(
        topics = "andesstay.events",
        groupId = "andesstay-group"
    )
    public void receiveEvent(String message) {

        System.out.println("=================================");
        System.out.println("EVENTO RECIBIDO DESDE KAFKA");
        System.out.println(message);
        System.out.println("=================================");
    }
}