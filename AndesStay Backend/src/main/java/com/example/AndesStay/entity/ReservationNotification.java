package com.example.AndesStay.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_notifications", indexes = {
    @Index(name = "idx_notice_audience_id", columnList = "audience,occurred_at,id"),
    @Index(name = "idx_notice_recipient_id", columnList = "recipient_id,occurred_at,id")
})
public class ReservationNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 16) private String audience;
    @Column(name = "recipient_id", length = 73) private String recipientId;
    @Column(name = "reservation_id", nullable = false) private Long reservationId;
    @Column(name = "unit_id", nullable = false) private Long unitId;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;

    protected ReservationNotification() {}
    public ReservationNotification(String audience, String recipientId, Long reservationId,
            Long unitId, String status, LocalDateTime occurredAt) {
        this.audience = audience; this.recipientId = recipientId;
        this.reservationId = reservationId; this.unitId = unitId;
        this.status = status; this.occurredAt = occurredAt;
    }
    public Long getId() { return id; }
    public String getAudience() { return audience; }
    public Long getReservationId() { return reservationId; }
    public Long getUnitId() { return unitId; }
    public String getStatus() { return status; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
