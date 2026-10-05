package com.example.AndesStay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AndesStayApplicationTests {
    @Autowired private ApplicationContext context;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @MockitoBean private org.springframework.amqp.core.AmqpAdmin amqp;
    @MockitoBean private org.springframework.kafka.core.KafkaTemplate<String, String> kafka;
    @Test void contextLoadsWithoutExternalServices() {
        assertNotNull(context.getBean("securityFilterChain"));
    }
}
