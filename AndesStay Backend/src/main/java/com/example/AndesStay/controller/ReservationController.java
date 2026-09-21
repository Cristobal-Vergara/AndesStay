package com.example.AndesStay.controller;

import com.example.AndesStay.entity.Reservation;
import com.example.AndesStay.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<Reservation> createReservation(
            @RequestBody Reservation reservation) {

    Reservation created = reservationService.createReservation(reservation);

    if (created == null) {
        return ResponseEntity.badRequest().build();
    }

    return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<Reservation>> getAllReservations() {

        return ResponseEntity.ok(
                reservationService.getAllReservations()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reservation> getReservationById(
            @PathVariable Long id) {

        return reservationService.getReservationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Reservation> updateReservation(
            @PathVariable Long id,
            @RequestBody Reservation reservation) {

        Reservation updated =
                reservationService.updateReservation(id, reservation);

        if (updated == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReservation(
            @PathVariable Long id) {

        boolean deleted = reservationService.deleteReservation(id);

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}/status")
public ResponseEntity<Reservation> updateStatus(
        @PathVariable Long id,
        @RequestBody Reservation reservation) {

    Reservation updated =
            reservationService.updateStatus(id, reservation.getStatus());

    if (updated == null) {
        return ResponseEntity.badRequest().build();
    }

    return ResponseEntity.ok(updated);
}
}