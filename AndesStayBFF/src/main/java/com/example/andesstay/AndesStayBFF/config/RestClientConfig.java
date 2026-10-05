package com.example.andesstay.AndesStayBFF.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder(@Value("${backend.connect-timeout-seconds:5}") long connect,
            @Value("${backend.read-timeout-seconds:15}") long read) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connect)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(read));
        return RestClient.builder().requestFactory(factory);
    }
}
