package com.example.AndesStayRabbitAdmin.config;

import com.example.AndesStayRabbitAdmin.client.RabbitManagementClient;
import com.example.AndesStayRabbitAdmin.service.RabbitResourceScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class RabbitManagementConfig {
    @Bean
    public RabbitManagementClient rabbitManagementClient(
            @Value("${andesstay.rabbitmq.management-url}") String url,
            @Value("${andesstay.rabbitmq.management-vhost}") String vhost,
            @Value("${spring.rabbitmq.username}") String username,
            @Value("${spring.rabbitmq.password}") String password,
            @Value("${andesstay.rabbitmq.management-connect-timeout-ms}") long connectTimeout,
            @Value("${andesstay.rabbitmq.management-read-timeout-ms}") long readTimeout) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout))
                .version(HttpClient.Version.HTTP_1_1).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        var client = RestClient.builder().requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password)).build();
        return new RabbitManagementClient(client, url, vhost);
    }

    @Bean
    public RabbitResourceScope rabbitResourceScope(Environment env) {
        var protectedNames = Stream.of("reservation-queue", "guest-queue", "exchange",
                "dead-letter-exchange", "dead-letter-queue", "demo-queue", "demo-dlx", "demo-dlq")
                .map(key -> env.getRequiredProperty("andesstay.rabbitmq." + key)).collect(Collectors.toSet());
        return new RabbitResourceScope(env.getRequiredProperty("andesstay.rabbitmq.admin-resource-prefix"),
                env.getRequiredProperty("andesstay.rabbitmq.reservation-queue"), protectedNames);
    }
}
