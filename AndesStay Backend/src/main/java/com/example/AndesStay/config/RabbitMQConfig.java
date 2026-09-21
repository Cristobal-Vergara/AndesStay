package com.example.AndesStay.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String RESERVATION_QUEUE = "andesstay.reservations";

    @Bean
    public Queue reservationQueue() {
        return new Queue(RESERVATION_QUEUE, true);
    }
}