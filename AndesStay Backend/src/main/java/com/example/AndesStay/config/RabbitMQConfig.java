package com.example.AndesStay.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.example.AndesStay.service.ReservationEventType;
import java.util.Map;

@Configuration
public class RabbitMQConfig {
    @Bean public Queue reservationQueue(
            @Value("${andesstay.rabbitmq.reservation-queue}") String name) {
        return new Queue(name, true);
    }
    @Bean public Queue guestNotificationQueue(
            @Value("${andesstay.rabbitmq.guest-queue}") String name) {
        return new Queue(name, true);
    }
    @Bean public DirectExchange reservationExchange(
            @Value("${andesstay.rabbitmq.exchange}") String name) {
        return new DirectExchange(name, true, false);
    }
    @Bean public FanoutExchange reservationDeadLetterExchange(
            @Value("${andesstay.rabbitmq.dead-letter-exchange}") String name) {
        return new FanoutExchange(name, true, false);
    }
    @Bean public Queue reservationDeadLetterQueue(
            @Value("${andesstay.rabbitmq.dead-letter-queue}") String name) {
        return new Queue(name, true);
    }
    @Bean public Binding reservationDeadBinding(@Qualifier("reservationDeadLetterQueue") Queue queue,
            @Qualifier("reservationDeadLetterExchange") FanoutExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange);
    }

    @Bean public Map<ReservationEventType, String> reservationRoutingKeys(
            @Value("${andesstay.rabbitmq.created-routing-key}") String created,
            @Value("${andesstay.rabbitmq.confirmed-routing-key}") String confirmed,
            @Value("${andesstay.rabbitmq.cancelled-routing-key}") String cancelled) {
        return Map.of(ReservationEventType.RESERVATION_CREATED, created,
                ReservationEventType.RESERVATION_CONFIRMED, confirmed,
                ReservationEventType.RESERVATION_CANCELLED, cancelled);
    }

    @Bean public Declarables reservationBindings(DirectExchange reservationExchange,
            @Qualifier("reservationQueue") Queue reception,
            @Qualifier("guestNotificationQueue") Queue guest,
            @Qualifier("reservationRoutingKeys") Map<ReservationEventType, String> keys) {
        // Recepcion recibe el ciclo completo; el huesped solo confirmaciones y cancelaciones.
        return new Declarables(
            BindingBuilder.bind(reception).to(reservationExchange).with(keys.get(ReservationEventType.RESERVATION_CREATED)),
            BindingBuilder.bind(reception).to(reservationExchange).with(keys.get(ReservationEventType.RESERVATION_CONFIRMED)),
            BindingBuilder.bind(reception).to(reservationExchange).with(keys.get(ReservationEventType.RESERVATION_CANCELLED)),
            BindingBuilder.bind(guest).to(reservationExchange).with(keys.get(ReservationEventType.RESERVATION_CONFIRMED)),
            BindingBuilder.bind(guest).to(reservationExchange).with(keys.get(ReservationEventType.RESERVATION_CANCELLED)));
    }
}
