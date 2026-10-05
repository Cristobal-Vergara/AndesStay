package com.example.AndesStay.service;

import tools.jackson.databind.json.JsonMapper;

public enum ReservationEventType {
    RESERVATION_CREATED("reservation.created", "CREADA"),
    RESERVATION_CONFIRMED("reservation.confirmed", "CONFIRMADA"),
    RESERVATION_CANCELLED("reservation.cancelled", "CANCELADA");

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final String routingKey;
    private final String status;

    ReservationEventType(String routingKey, String status) {
        this.routingKey = routingKey;
        this.status = status;
    }
    public String routingKey() { return routingKey; }
    public String status() { return status; }
    public static ReservationEventType fromPayload(String payload) {
        return valueOf(JSON.readTree(payload).path("type").asString());
    }
}
