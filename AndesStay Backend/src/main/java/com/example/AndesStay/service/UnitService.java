package com.example.AndesStay.service;

import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.repository.UnitRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UnitService {

    private final UnitRepository unitRepository;

    public UnitService(UnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    public List<Unit> getAllUnits() {
        return unitRepository.findAll();
    }

    public Unit getUnitById(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada"));
    }

    public Unit createUnit(Unit unit) {
        return unitRepository.save(unit);
    }

    public Unit updateUnit(Long id, Unit unit) {

        Unit existingUnit = getUnitById(id);

        existingUnit.setName(unit.getName());
        existingUnit.setType(unit.getType());
        existingUnit.setCapacity(unit.getCapacity());
        existingUnit.setPrice(unit.getPrice());
        existingUnit.setAvailable(unit.getAvailable());
        existingUnit.setDescription(unit.getDescription());

        return unitRepository.save(existingUnit);
    }

    public void deleteUnit(Long id) {

        Unit existingUnit = getUnitById(id);

        unitRepository.delete(existingUnit);
    }
}