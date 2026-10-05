package com.example.AndesStay.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "consumed_events")
@IdClass(ConsumedEvent.Key.class)
public class ConsumedEvent {
    @Id @Column(length = 16) private String destination;
    @Id @Column(length = 80) private String eventId;
    @Column(nullable = false) private LocalDateTime receivedAt;

    public static class Key implements Serializable {
        public String destination;
        public String eventId;
        public Key() {}
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(destination, key.destination)
                    && Objects.equals(eventId, key.eventId);
        }
        @Override public int hashCode() { return Objects.hash(destination, eventId); }
    }
}
