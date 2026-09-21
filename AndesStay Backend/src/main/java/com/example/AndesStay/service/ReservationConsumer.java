package com.example.AndesStay.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class ReservationConsumer {

    @RabbitListener(queues = "andesstay.reservations")
    public void receiveReservation(String message) {

        System.out.println("=================================");
        System.out.println("MENSAJE RECIBIDO DESDE RABBITMQ");
        System.out.println(message);
        System.out.println("=================================");
    }
}