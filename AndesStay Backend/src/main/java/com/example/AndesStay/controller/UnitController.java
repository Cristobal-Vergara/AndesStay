package com.example.AndesStay.controller;

import com.example.AndesStay.dto.PageResponse;
import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.service.UnitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/catalog/units", "/api/catalog/units"})
public class UnitController {
    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Unit> getAllUnits(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.getUnits(page, size));
    }

    @GetMapping("/{id}")
    public Unit getUnitById(@PathVariable Long id) {
        return service.getUnitById(id);
    }

    @PostMapping
    public ResponseEntity<Unit> createUnit(@RequestBody Unit unit) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUnit(unit));
    }

    @PutMapping("/{id}")
    public Unit updateUnit(@PathVariable Long id, @RequestBody Unit unit) {
        return service.updateUnit(id, unit);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUnit(@PathVariable Long id) {
        service.deleteUnit(id);
        return ResponseEntity.noContent().build();
    }
}
