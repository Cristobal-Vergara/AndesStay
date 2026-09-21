package com.example.AndesStay.controller;

import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.service.UnitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/units")
@CrossOrigin(origins = "*")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public ResponseEntity<List<Unit>> getAllUnits() {

        return ResponseEntity.ok(
                unitService.getAllUnits()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Unit> getUnitById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                unitService.getUnitById(id)
        );
    }

    @PostMapping
    public ResponseEntity<Unit> createUnit(
            @RequestBody Unit unit) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(unitService.createUnit(unit));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Unit> updateUnit(
            @PathVariable Long id,
            @RequestBody Unit unit) {

        return ResponseEntity.ok(
                unitService.updateUnit(id, unit)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUnit(
            @PathVariable Long id) {

        unitService.deleteUnit(id);

        return ResponseEntity.noContent().build();
    }
}