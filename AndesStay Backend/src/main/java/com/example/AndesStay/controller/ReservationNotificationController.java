package com.example.AndesStay.controller;

import com.example.AndesStay.dto.PageResponse;
import com.example.AndesStay.dto.ReservationNotificationResponse;
import com.example.AndesStay.service.ReservationNotificationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/notifications", "/api/notifications"})
public class ReservationNotificationController {
    private final ReservationNotificationService service;
    public ReservationNotificationController(ReservationNotificationService service) { this.service = service; }

    @GetMapping
    public PageResponse<ReservationNotificationResponse> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        return PageResponse.from(service.list(page, size));
    }
}
