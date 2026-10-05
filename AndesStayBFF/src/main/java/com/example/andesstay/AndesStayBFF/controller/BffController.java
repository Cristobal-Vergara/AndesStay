package com.example.andesstay.AndesStayBFF.controller;

import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bff")
public class BffController {
    private final BffReservationService service;
    public BffController(BffReservationService service) { this.service = service; }

    @GetMapping("/test")
    public String test() { return "BFF AndesStay funcionando"; }

    @GetMapping("/reservations")
    public ResponseEntity<String> reservations(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return json(page == null && size == null ? service.getReservations(jwt.getTokenValue())
                : service.getReservations(jwt.getTokenValue(), page, size));
    }
    @GetMapping("/reservations/{id}")
    public ResponseEntity<String> reservation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return json(service.getReservation(jwt.getTokenValue(), id));
    }
    @PostMapping("/reservations")
    public ResponseEntity<String> createReservation(@AuthenticationPrincipal Jwt jwt, @RequestBody String body) {
        return json(service.createReservation(jwt.getTokenValue(), body));
    }
    @PutMapping("/reservations/{id}")
    public ResponseEntity<String> updateReservation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody String body) {
        return json(service.updateReservation(jwt.getTokenValue(), id, body));
    }
    @PutMapping("/reservations/{id}/status")
    public ResponseEntity<String> status(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody String body) {
        return json(service.updateReservationStatus(jwt.getTokenValue(), id, body));
    }
    @PutMapping("/reservations/{id}/owner")
    public ResponseEntity<String> owner(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody String body) {
        return json(service.assignOwner(jwt.getTokenValue(), id, body));
    }
    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<Void> deleteReservation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.deleteReservation(jwt.getTokenValue(), id); return ResponseEntity.noContent().build();
    }

    @GetMapping("/catalog")
    public ResponseEntity<String> catalog(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return json(page == null && size == null ? service.getCatalog(jwt.getTokenValue())
                : service.getCatalog(jwt.getTokenValue(), page, size));
    }
    @GetMapping("/catalog/{id}")
    public ResponseEntity<String> unit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return json(service.getUnit(jwt.getTokenValue(), id));
    }
    @PostMapping("/catalog")
    public ResponseEntity<String> createUnit(@AuthenticationPrincipal Jwt jwt, @RequestBody String body) {
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                .body(service.createUnit(jwt.getTokenValue(), body));
    }
    @PutMapping("/catalog/{id}")
    public ResponseEntity<String> updateUnit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody String body) {
        return json(service.updateUnit(jwt.getTokenValue(), id, body));
    }
    @DeleteMapping("/catalog/{id}")
    public ResponseEntity<Void> deleteUnit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.deleteUnit(jwt.getTokenValue(), id); return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports")
    public ResponseEntity<String> reports(@AuthenticationPrincipal Jwt jwt) { return json(service.getReports(jwt.getTokenValue())); }

    @GetMapping("/reports/operations")
    public ResponseEntity<String> operations(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String date) {
        return json(service.getOperations(jwt.getTokenValue(), date));
    }
    @GetMapping("/messaging/status")
    public ResponseEntity<String> messagingStatus(@AuthenticationPrincipal Jwt jwt) {
        return json(service.getMessagingStatus(jwt.getTokenValue()));
    }
    @PostMapping("/messaging/demo")
    public ResponseEntity<String> messagingDemo(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().contentType(MediaType.APPLICATION_JSON)
                .body(service.publishMessagingDemo(jwt.getTokenValue()));
    }
    @GetMapping("/notifications")
    public ResponseEntity<String> notifications(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "5") Integer size) {
        return json(service.getNotifications(jwt.getTokenValue(), page, size));
    }
    @GetMapping("/account")
    public ResponseEntity<String> account(@AuthenticationPrincipal Jwt jwt) { return json(service.getAccount(jwt.getTokenValue())); }
    @GetMapping("/account/profile")
    public ResponseEntity<String> profile(@AuthenticationPrincipal Jwt jwt) { return json(service.getProfile(jwt.getTokenValue())); }
    @PostMapping("/account/register")
    public ResponseEntity<String> registerProfile(@AuthenticationPrincipal Jwt jwt, @RequestBody String body) {
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                .body(service.registerProfile(jwt.getTokenValue(), body));
    }

    @GetMapping("/audit")
    public ResponseEntity<String> audit(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String user, @RequestParam(required = false) String action,
            @RequestParam(required = false) String inicio, @RequestParam(required = false) String fin,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return json(service.getAudit(jwt.getTokenValue(), user, action, inicio, fin, page, size));
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
