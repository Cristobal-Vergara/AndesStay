package com.example.AndesStay.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String RESERVATION_QUEUE = "andesstay.reservations";
    public static final String GUEST_QUEUE = "andesstay.guest-notifications";
    public static final String EXCHANGE = "andesstay.reservations.direct";

    @Bean public Queue reservationQueue(
            @Value("${andesstay.rabbitmq.reservation-queue:andesstay.reservations}") String name) {
        return new Queue(name, true);
    }
    @Bean public Queue guestNotificationQueue(
            @Value("${andesstay.rabbitmq.guest-queue:andesstay.guest-notifications}") String name) {
        return new Queue(name, true);
    }
    @Bean public DirectExchange reservationExchange(
            @Value("${andesstay.rabbitmq.exchange:andesstay.reservations.direct}") String name) {
        return new DirectExchange(name, true, false);
    }
    @Bean public FanoutExchange reservationDeadLetterExchange(
            @Value("${andesstay.rabbitmq.dead-letter-exchange:andesstay.reservations.dlx}") String name) {
        return new FanoutExchange(name, true, false);
    }
    @Bean public Queue reservationDeadLetterQueue(
            @Value("${andesstay.rabbitmq.dead-letter-queue:andesstay.reservations.dlq}") String name) {
        return new Queue(name, true);
    }
    @Bean public Binding reservationDeadBinding(@Qualifier("reservationDeadLetterQueue") Queue queue,
            @Qualifier("reservationDeadLetterExchange") FanoutExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange);
    }

    @Bean public Declarables reservationBindings(DirectExchange reservationExchange,
            @Qualifier("reservationQueue") Queue reception,
            @Qualifier("guestNotificationQueue") Queue guest) {
        return new Declarables(
            BindingBuilder.bind(reception).to(reservationExchange).with("reservation.created"),
            BindingBuilder.bind(reception).to(reservationExchange).with("reservation.confirmed"),
            BindingBuilder.bind(reception).to(reservationExchange).with("reservation.cancelled"),
            BindingBuilder.bind(guest).to(reservationExchange).with("reservation.confirmed"),
            BindingBuilder.bind(guest).to(reservationExchange).with("reservation.cancelled"));
    }
}
