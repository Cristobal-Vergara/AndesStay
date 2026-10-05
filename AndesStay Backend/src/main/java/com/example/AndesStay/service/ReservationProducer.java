package com.example.AndesStay.service;

import com.example.AndesStay.entity.Reservation;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ReservationProducer {
    private final OutboxService outbox;
    public ReservationProducer(OutboxService outbox) { this.outbox = outbox; }

    public void sendReservationCreated(Reservation reservation) {
        enqueue(ReservationEventType.RESERVATION_CREATED, reservation);
    }
    public void sendReservationStatusChanged(Reservation reservation) {
        switch (reservation.getStatus()) {
            case "CONFIRMADA" -> enqueue(ReservationEventType.RESERVATION_CONFIRMED, reservation);
            case "CANCELADA" -> enqueue(ReservationEventType.RESERVATION_CANCELLED, reservation);
            default -> { }
        }
    }
    private void enqueue(ReservationEventType type, Reservation reservation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reservationId", reservation.getId());
        data.put("unitId", reservation.getUnitId());
        data.put("checkIn", reservation.getCheckIn().toString());
        data.put("checkOut", reservation.getCheckOut().toString());
        data.put("status", reservation.getStatus());
        data.put("guestAssigned", reservation.isGuestAssigned());
        if (reservation.getOwnerId() != null) data.put("ownerId", reservation.getOwnerId());
        outbox.enqueue("RABBITMQ", type.name(), data);
    }
}
