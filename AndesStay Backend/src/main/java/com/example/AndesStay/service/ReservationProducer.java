package com.example.AndesStay.service;

import com.example.AndesStay.config.RabbitMQConfig;
import com.example.AndesStay.entity.Reservation;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReservationProducer {

    private final RabbitTemplate rabbitTemplate;

    public ReservationProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendReservationCreated(Reservation reservation) {

        String message =
                "Reserva creada: "
                + "ID=" + reservation.getId()
                + ", Huésped=" + reservation.getGuestName()
                + ", Unidad=" + reservation.getUnitId()
                + ", CheckIn=" + reservation.getCheckIn()
                + ", CheckOut=" + reservation.getCheckOut();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RESERVATION_QUEUE,
                message
        );
    }
}