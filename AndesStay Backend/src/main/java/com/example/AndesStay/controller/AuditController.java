package com.example.AndesStay.controller;

import com.example.AndesStay.entity.AuditEvent;
import com.example.AndesStay.service.AuditService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import com.example.AndesStay.dto.PageResponse;

@RestController
@RequestMapping({"/api/v1/audit", "/api/audit"})
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AuditEvent> getEvents(
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.from(auditService.getFilteredEventsPage(
                user,
                action,
                parseDate(inicio, "inicio"),
                parseDate(fin, "fin"), page, size
        ));
    }

    private LocalDateTime parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El filtro " + field + " debe ser una fecha y hora válida, por ejemplo 2026-10-02T14:30");
        }
    }
}
