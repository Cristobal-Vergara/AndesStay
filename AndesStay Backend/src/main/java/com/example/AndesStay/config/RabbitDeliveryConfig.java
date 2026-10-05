package com.example.AndesStay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitDeliveryConfig {
    private static final Logger log = LoggerFactory.getLogger(RabbitDeliveryConfig.class);

    @Bean
    public MessageRecoverer reservationMessageRecoverer() {
        return (message, cause) -> {
            log.warn("Reintentos de entrega agotados en la cola {}", message.getMessageProperties().getConsumerQueue());
            // El contenedor rechaza sin reencolar; RabbitMQ aplica el DLX de la cola.
            throw new AmqpRejectAndDontRequeueException("Reintentos de entrega agotados");
        };
    }
}
