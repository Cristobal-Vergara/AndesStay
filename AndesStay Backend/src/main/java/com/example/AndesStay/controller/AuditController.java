package com.example.AndesStay.controller;

import com.example.AndesStay.entity.AuditEvent;
import com.example.AndesStay.service.AuditService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEvent> getEvents(
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fin) {

        LocalDateTime fechaInicio = null;
        LocalDateTime fechaFin = null;

        if (inicio != null && !inicio.isBlank()) {

            fechaInicio =
                    LocalDateTime.parse(inicio);
        }

        if (fin != null && !fin.isBlank()) {

            fechaFin =
                    LocalDateTime.parse(fin);
        }

        return auditService.getFilteredEvents(
                user,
                action,
                fechaInicio,
                fechaFin
        );
    }
}