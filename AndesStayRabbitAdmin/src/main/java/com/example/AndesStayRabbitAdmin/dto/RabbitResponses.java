package com.example.AndesStayRabbitAdmin.dto;

import java.util.List;
import java.util.Map;

public final class RabbitResponses {
    private RabbitResponses() {}

    public record Page<T>(List<T> items, int page, int pageSize, long total) {}
    public record QueueView(String name, boolean durable, boolean autoDelete, Long messagesReady,
                            Long messagesUnacknowledged, Integer consumers, Map<String, Object> policy,
                            boolean writable) {}
    public record ExchangeView(String name, String type, boolean durable, Map<String, Object> policy,
                               boolean writable) {}
    public record BindingView(String exchange, String queue, String routingKey, String propertiesKey,
                              boolean writable) {}
    public record Status(boolean connected, String reservationQueue, Long messagesReady,
                         Long messagesUnacknowledged, Integer consumers) {}
    public record Error(String code, String message, List<FieldError> details) {}
    public record FieldError(String field, String message) {}
}
