package com.example.AndesStay.service;

import tools.jackson.databind.json.JsonMapper;

public enum ReservationEventType {
    RESERVATION_CREATED("CREADA"),
    RESERVATION_CONFIRMED("CONFIRMADA"),
    RESERVATION_CANCELLED("CANCELADA");

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final String status;

    ReservationEventType(String status) {
        this.status = status;
    }
    public String status() { return status; }
    public static ReservationEventType fromPayload(String payload) {
        return valueOf(JSON.readTree(payload).path("type").asString());
    }
}
