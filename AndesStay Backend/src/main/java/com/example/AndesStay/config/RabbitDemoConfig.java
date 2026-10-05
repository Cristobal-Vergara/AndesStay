package com.example.AndesStay.config;

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
            @Value("${andesstay.rabbitmq.demo-dlx:andesstay.delivery-demo.dlx}") String name) {
        return new FanoutExchange(name, true, false);
    }
    @Bean public Queue demoQueue(
            @Value("${andesstay.rabbitmq.demo-queue:andesstay.delivery-demo}") String name,
            @Qualifier("demoDeadLetterExchange") FanoutExchange exchange) {
        return QueueBuilder.durable(name).ttl(10000).maxLength(10)
            .deadLetterExchange(exchange.getName()).build();
    }
    @Bean public Queue demoDeadLetterQueue(
            @Value("${andesstay.rabbitmq.demo-dlq:andesstay.delivery-demo.dlq}") String name,
            @Value("${andesstay.messaging.demo-retention-ms:60000}") int retention) {
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
}
