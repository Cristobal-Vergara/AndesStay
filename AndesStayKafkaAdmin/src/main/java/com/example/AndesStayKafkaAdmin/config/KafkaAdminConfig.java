package com.example.AndesStayKafkaAdmin.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaAdminConfig {

    @Bean
    public AdminClient adminClient() {

        Map<String, Object> properties = new HashMap<>();

        properties.put(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092"
        );

        return AdminClient.create(properties);
    }
}