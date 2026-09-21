package com.example.andesstay.AndesStayBFF.controller;

import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bff")
public class BffController {

    private final BffReservationService reservationService;

    public BffController(
            BffReservationService reservationService) {

        this.reservationService = reservationService;
    }

    @GetMapping("/test")
    public String test() {
        return "BFF AndesStay funcionando";
    }

    @GetMapping("/reservations")
    public ResponseEntity<String> getReservations(
            @RequestHeader("Authorization") String authorization) {

        String token =
                authorization.replace("Bearer ", "");

        String reservations =
                reservationService.getReservations(token);

        return ResponseEntity.ok(reservations);
    }

    @PostMapping("/reservations")
    public ResponseEntity<String> createReservation(
            @RequestHeader("Authorization") String authorization,
            @RequestBody String body) {

        String token =
                authorization.replace("Bearer ", "");

        String reservation =
                reservationService.createReservation(
                        token,
                        body
                );

        return ResponseEntity.ok(reservation);
    }

    @PutMapping("/reservations/{reservationId}/status")
    public ResponseEntity<String> updateReservationStatus(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long reservationId,
            @RequestBody String body) {

        String token =
                authorization.replace("Bearer ", "");

        String reservation =
                reservationService.updateReservationStatus(
                        token,
                        reservationId,
                        body
                );

        return ResponseEntity.ok(reservation);
    }

    @GetMapping("/catalog")
    public ResponseEntity<String> getCatalog(
            @RequestHeader("Authorization") String authorization) {

        String token =
                authorization.replace("Bearer ", "");

        String catalog =
                reservationService.getCatalog(token);

        return ResponseEntity.ok(catalog);
    }

    @GetMapping("/reports")
    public ResponseEntity<String> getReports(
            @RequestHeader("Authorization") String authorization) {

        String token =
                authorization.replace("Bearer ", "");

        String reports =
                reservationService.getReports(token);

        return ResponseEntity.ok(reports);
    }

    @GetMapping("/audit")
    public ResponseEntity<String> getAudit(
            @RequestHeader("Authorization") String authorization) {

        String token =
                authorization.replace("Bearer ", "");

        String audit =
                reservationService.getAudit(token);

        return ResponseEntity.ok(audit);
    }
}