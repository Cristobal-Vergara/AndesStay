package com.example.AndesStay.service;

import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.repository.UnitRepository;
import com.example.AndesStay.repository.ReservationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class UnitService {
    private final UnitRepository units;
    private final ReservationRepository reservations;
    private final ReservationAccess access;

    public UnitService(UnitRepository units, ReservationRepository reservations, ReservationAccess access) {
        this.units = units;
        this.reservations = reservations;
        this.access = access;
    }

    public List<Unit> getAllUnits() {
        return units.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Page<Unit> getUnits(int page, int size) {
        return units.findAll(Pagination.request(page, size, Sort.by("id")));
    }

    public Unit getUnitById(Long id) {
        return units.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidad no encontrada"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Unit createUnit(Unit unit) {
        access.requireStaff();
        validate(unit);
        unit.setId(null);
        normalize(unit);
        return units.save(unit);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Unit updateUnit(Long id, Unit unit) {
        access.requireStaff();
        validate(unit);
        Unit current = lockUnit(id);
        normalize(unit);
        current.setName(unit.getName());
        current.setType(unit.getType());
        current.setCapacity(unit.getCapacity());
        current.setPrice(unit.getPrice());
        current.setAvailable(unit.getAvailable());
        current.setDescription(unit.getDescription());
        return units.save(current);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteUnit(Long id) {
        access.requireStaff();
        Unit current = lockUnit(id);
        if (reservations.existsByUnitId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar una unidad que tiene reservas asociadas");
        }
        units.delete(current);
    }

    private Unit lockUnit(Long id) {
        return units.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidad no encontrada"));
    }

    private void validate(Unit unit) {
        if (unit == null || invalidText(unit.getName()) || invalidText(unit.getType())) {
            throw badRequest("El nombre y el tipo son obligatorios y admiten hasta 255 caracteres");
        }
        if (unit.getCapacity() == null || unit.getCapacity() < 1) {
            throw badRequest("La capacidad debe ser un entero positivo");
        }
        if (unit.getPrice() == null || unit.getPrice().signum() <= 0
                || unit.getPrice().stripTrailingZeros().scale() > 2
                || unit.getPrice().precision() - unit.getPrice().scale() > 36) {
            throw badRequest("La tarifa debe ser positiva y admitir como máximo dos decimales");
        }
        if (unit.getAvailable() == null) {
            throw badRequest("Debe indicar si la unidad está habilitada");
        }
        if (unit.getDescription() != null && unit.getDescription().trim().length() > 255) {
            throw badRequest("La descripción admite hasta 255 caracteres");
        }
    }

    private boolean invalidText(String text) {
        return text == null || text.isBlank() || text.trim().length() > 255;
    }

    private void normalize(Unit unit) {
        unit.setName(unit.getName().trim());
        unit.setType(unit.getType().trim());
        unit.setPrice(unit.getPrice().setScale(2));
        unit.setDescription(unit.getDescription() == null ? null : unit.getDescription().trim());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
