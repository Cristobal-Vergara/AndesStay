package com.example.AndesStay.controller;

import com.example.AndesStay.service.ReservationAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Clock;
import java.util.Map;

@RestController
public class AccountController {
    private final ReservationAccess access;
    private final Clock clock;

    public AccountController(ReservationAccess access, Clock clock) {
        this.access = access;
        this.clock = clock;
    }

    @GetMapping("/api/account")
    public Map<String, String> account() {
        String[] identity = access.currentOwnerId().split(":", 2);
        return Map.of("tenantId", identity[0], "objectId", identity[1],
                "timeZone", clock.getZone().getId());
    }
}
