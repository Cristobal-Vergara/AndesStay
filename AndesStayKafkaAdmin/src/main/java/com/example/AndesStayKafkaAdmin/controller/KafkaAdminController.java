package com.example.AndesStayKafkaAdmin.controller;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.TopicDescription;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kafka")
public class KafkaAdminController {

    private final AdminClient adminClient;

    public KafkaAdminController(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    @GetMapping("/status")
    public String status() {
        return "Kafka Admin funcionando";
    }

    @GetMapping("/topics")
    public Collection<String> topics()
            throws InterruptedException, ExecutionException {

        return adminClient
                .listTopics()
                .names()
                .get();
    }

    @GetMapping("/topic/{topicName}")
    public Map<String, Object> topic(
            @PathVariable String topicName)
            throws InterruptedException, ExecutionException {

        TopicDescription description =
                adminClient
                        .describeTopics(
                                java.util.List.of(topicName)
                        )
                        .allTopicNames()
                        .get()
                        .get(topicName);

        Map<String, Object> result = new HashMap<>();

        result.put("name", description.name());
        result.put(
                "partitions",
                description.partitions().size()
        );
        result.put(
                "replicationFactor",
                description.partitions()
                        .get(0)
                        .replicas()
                        .size()
        );

        return result;
    }
}