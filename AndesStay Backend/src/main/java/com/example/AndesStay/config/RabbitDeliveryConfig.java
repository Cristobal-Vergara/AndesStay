package com.example.AndesStay.config;

import com.example.AndesStay.messaging.delivery.RabbitDeliveryFailureClassifier;
import com.example.AndesStay.messaging.delivery.RabbitDeliveryHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import java.time.Duration;

@Configuration
public class RabbitDeliveryConfig {
    @Bean
    public RabbitDeliveryHandler rabbitDeliveryHandler(
            @Value("${andesstay.rabbitmq.delivery.max-retries}") long maxRetries,
            @Value("${andesstay.rabbitmq.delivery.initial-interval-ms}") long initialInterval,
            @Value("${andesstay.rabbitmq.delivery.multiplier}") double multiplier,
            @Value("${andesstay.rabbitmq.delivery.max-interval-ms}") long maxInterval) {
        var policy = RetryPolicy.builder().maxRetries(maxRetries)
                .delay(Duration.ofMillis(initialInterval)).multiplier(multiplier)
                .maxDelay(Duration.ofMillis(maxInterval)).includes(Exception.class)
                .predicate(RabbitDeliveryFailureClassifier::retryable).build();
        return new RabbitDeliveryHandler(new RetryTemplate(policy));
    }
}
