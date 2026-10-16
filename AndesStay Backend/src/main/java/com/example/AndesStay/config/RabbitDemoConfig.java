package com.example.AndesStay.config;

import com.example.AndesStay.messaging.demo.DeliveryDemoConsumer;
import com.example.AndesStay.messaging.delivery.RabbitDeliveryHandler;
import com.example.AndesStay.service.MessagingDemoService;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name="andesstay.messaging.demo-enabled", havingValue="true")
public class RabbitDemoConfig {
    @Bean public FanoutExchange demoDeadLetterExchange(
            @Value("${andesstay.rabbitmq.demo-dlx}") String name) {
        return new FanoutExchange(name, true, false);
    }
    @Bean public Queue demoQueue(
            @Value("${andesstay.rabbitmq.demo-queue}") String name,
            @Value("${andesstay.rabbitmq.demo-message-ttl-ms}") int ttl,
            @Value("${andesstay.rabbitmq.demo-max-length}") int maxLength,
            @Qualifier("demoDeadLetterExchange") FanoutExchange exchange) {
        return QueueBuilder.durable(name).ttl(ttl).maxLength(maxLength)
            .deadLetterExchange(exchange.getName()).build();
    }
    @Bean public Queue demoDeadLetterQueue(
            @Value("${andesstay.rabbitmq.demo-dlq}") String name,
            @Value("${andesstay.messaging.demo-retention-ms}") int retention) {
        return QueueBuilder.durable(name).ttl(retention).build();
    }
    @Bean public Binding demoDeadBinding(@Qualifier("demoDeadLetterQueue") Queue queue,
            @Qualifier("demoDeadLetterExchange") FanoutExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange);
    }
    @Bean public MessagingDemoService messagingDemoService(RabbitTemplate rabbit,
            @Qualifier("demoQueue") Queue source, @Qualifier("demoDeadLetterQueue") Queue dead) {
        return new MessagingDemoService(rabbit, source.getName(), dead.getName());
    }
    @Bean public DeliveryDemoConsumer deliveryDemoConsumer(RabbitDeliveryHandler delivery) {
        return new DeliveryDemoConsumer(delivery);
    }
}
