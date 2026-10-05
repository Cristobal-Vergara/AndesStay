package com.example.AndesStay.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_ready", columnList = "published_at,next_attempt_at,created_at"))
public class OutboxEvent {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 16) private String destination;
    @Lob @Column(nullable = false, columnDefinition = "longtext") private String payload;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime nextAttemptAt;
    private LocalDateTime publishedAt;
    @Column(nullable = false) private int attempts;

    public OutboxEvent() {}
    public OutboxEvent(String id, String destination, String payload, LocalDateTime createdAt) {
        this.id = id; this.destination = destination; this.payload = payload;
        this.createdAt = createdAt; this.nextAttemptAt = createdAt;
    }
    public String getId() { return id; }
    public String getDestination() { return destination; }
    public String getPayload() { return payload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public void published(LocalDateTime now) { attempts++; publishedAt = now; }
    public void retryAt(LocalDateTime next) { attempts++; nextAttemptAt = next; }
}
