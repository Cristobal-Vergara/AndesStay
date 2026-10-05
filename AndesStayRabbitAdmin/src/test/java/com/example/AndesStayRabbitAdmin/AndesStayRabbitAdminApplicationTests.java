package com.example.AndesStayRabbitAdmin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AndesStayRabbitAdminApplicationTests {
    @Autowired private ApplicationContext context;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @Test void contextLoadsWithoutExternalServices() {
        assertNotNull(context.getBean("securityFilterChain"));
    }
}
