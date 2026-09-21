package com.example.AndesStay.controller;

import com.example.AndesStay.service.ReportService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getReservationKPIs() {

        return ResponseEntity.ok(
                reportService.getReservationKPIs()
        );
    }

    @GetMapping("/secure")
    public ResponseEntity<String> secureEndpoint() {

        return ResponseEntity.ok(
                "Acceso autorizado mediante JWT de Azure AD"
        );
    }
}