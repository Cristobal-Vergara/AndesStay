package com.example.AndesStay.dto;

import java.time.Instant;

public record ReservationNotificationResponse(Long id, Long reservationId, Long unitId,
        String status, String title, Instant occurredAt) {}
