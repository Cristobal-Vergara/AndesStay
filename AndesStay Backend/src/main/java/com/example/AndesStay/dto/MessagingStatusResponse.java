package com.example.AndesStay.dto;

import java.time.Instant;

public record MessagingStatusResponse(boolean available, Long pending, Long quarantined,
        Long receptionConsumers, Long guestConsumers, boolean demoEnabled,
        Long demoQuarantined, Instant checkedAt) {}
