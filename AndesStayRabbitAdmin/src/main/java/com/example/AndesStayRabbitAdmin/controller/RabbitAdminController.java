package com.example.AndesStayRabbitAdmin.controller;

import java.util.Properties;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rabbitmq")
public class RabbitAdminController {

    private final RabbitAdmin rabbitAdmin;

    public RabbitAdminController(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    @GetMapping("/status")
    public String status() {

        rabbitAdmin.getQueueProperties(
                "andesstay.reservations"
        );

        return "RabbitMQ conectado y funcionando";
    }

    @GetMapping("/queue/{queueName}")
    public Properties getQueue(
            @PathVariable String queueName) {

        Properties properties =
                rabbitAdmin.getQueueProperties(queueName);

        if (properties == null) {
            return null;
        }

        return properties;
    }
}