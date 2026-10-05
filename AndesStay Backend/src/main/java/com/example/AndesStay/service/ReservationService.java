package com.example.AndesStay.service;

import com.example.AndesStay.entity.Reservation;
import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.repository.ReservationRepository;
import com.example.AndesStay.repository.UnitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ReservationService {

    private static final List<String> BLOCKING_STATUSES =
            List.of("CONFIRMADA", "CHECKIN_PENDIENTE", "EN_ESTADÍA");

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "CREADA", Set.of("CONFIRMADA", "CANCELADA"),
            "CONFIRMADA", Set.of("CHECKIN_PENDIENTE", "CANCELADA"),
            "CHECKIN_PENDIENTE", Set.of("EN_ESTADÍA"),
            "EN_ESTADÍA", Set.of("CHECKOUT")
    );

    private final ReservationRepository reservationRepository;
    private final UnitRepository unitRepository;
    private final ReservationProducer reservationProducer;
    private final KafkaProducer kafkaProducer;
    private final AuditService auditService;
    private final ReservationAccess reservationAccess;

    public ReservationService(
            ReservationRepository reservationRepository, UnitRepository unitRepository,
            ReservationProducer reservationProducer, KafkaProducer kafkaProducer,
            AuditService auditService, ReservationAccess reservationAccess) {
        this.reservationRepository = reservationRepository;
        this.unitRepository = unitRepository;
        this.reservationProducer = reservationProducer;
        this.kafkaProducer = kafkaProducer;
        this.auditService = auditService;
        this.reservationAccess = reservationAccess;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation createReservation(Reservation reservation) {
        String ownerId = reservationAccess.currentOwnerId();
        validateDetails(reservation);
        Unit unit = lockUnit(reservation.getUnitId());
        requireAvailable(unit);
        requireNoOverlap(reservation, null);

        // El servidor decide identidad y estado, incluso si el cliente envía otros.
        reservation.setId(null);
        reservation.setStatus("CREADA");
        reservation.setOwnerId(ownerId);
        reservation.setGuestAssigned(!reservationAccess.isStaff());
        reservation.setGuestName(reservation.getGuestName().trim());

        Reservation saved = reservationRepository.save(reservation);
        reservationProducer.sendReservationCreated(saved);
        kafkaProducer.sendReservationEvent(
                "Reserva creada: ID=" + saved.getId()
                        + ", Huésped=" + saved.getGuestName() + ", Unidad=" + saved.getUnitId()
                        + ", CheckIn=" + saved.getCheckIn() + ", CheckOut=" + saved.getCheckOut()
                        + ", Estado=" + saved.getStatus());
        auditService.registerEvent("RESERVA_CREADA", saved.getId(),
                "Se creó la reserva para el huésped " + saved.getGuestName());
        return saved;
    }

    public List<Reservation> getReservations() {
        if (reservationAccess.isStaff()) {
            return reservationRepository.findAll();
        }
        return reservationRepository.findByOwnerId(reservationAccess.currentOwnerId());
    }

    public Optional<Reservation> getReservationById(Long id) {
        if (reservationAccess.isStaff()) {
            return reservationRepository.findById(id);
        }
        // Una reserva ajena y una inexistente producen la misma respuesta: 404.
        return reservationRepository.findByIdAndOwnerId(id, reservationAccess.currentOwnerId());
    }

    @Transactional(readOnly = true)
    public Page<Reservation> getReservations(int page, int size) {
        var request = Pagination.request(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return reservationAccess.isStaff() ? reservationRepository.findAll(request)
                : reservationRepository.findByOwnerId(reservationAccess.currentOwnerId(), request);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation assignOwner(Long id, String objectId) {
        reservationAccess.requireStaff();
        if (objectId == null || !objectId.trim().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El identificador de cuenta del huésped debe ser un OID válido de Microsoft");
        }
        String tenant = reservationAccess.currentOwnerId().split(":", 2)[0];
        Reservation reservation = lockReservation(id);
        reservation.setOwnerId(tenant + ":" + UUID.fromString(objectId.trim()));
        reservation.setGuestAssigned(true);
        Reservation saved = reservationRepository.save(reservation);
        auditService.registerEvent("PROPIETARIO_ASIGNADO", saved.getId(),
                "Recepción asignó explícitamente la reserva a la cuenta del huésped");
        return saved;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation updateReservation(Long id, Reservation incoming) {
        reservationAccess.requireStaff();
        Reservation current = lockReservation(id);
        validateDetails(incoming);

        // Orden estable al cambiar de unidad para evitar bloqueos en orden inverso.
        Unit targetUnit = lockUnits(current.getUnitId(), incoming.getUnitId());
        requireAvailable(targetUnit);
        if ("CREADA".equals(current.getStatus()) || BLOCKING_STATUSES.contains(current.getStatus())) {
            requireNoOverlap(incoming, current.getId());
        }

        // No modificar la entidad administrada hasta terminar todas las validaciones.
        current.setGuestName(incoming.getGuestName().trim());
        current.setUnitId(incoming.getUnitId());
        current.setCheckIn(incoming.getCheckIn());
        current.setCheckOut(incoming.getCheckOut());
        Reservation saved = reservationRepository.save(current);
        auditService.registerEvent("RESERVA_ACTUALIZADA", saved.getId(),
                "Se actualizaron los datos y fechas de la reserva");
        return saved;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean deleteReservation(Long id) {
        reservationAccess.requireStaff();
        Optional<Reservation> existing = reservationRepository.findByIdForUpdate(id);
        if (existing.isEmpty()) {
            return false;
        }
        // Una reserva huérfana anterior también puede eliminarse para reparar sus datos.
        unitRepository.findByIdForUpdate(existing.get().getUnitId());
        reservationRepository.delete(existing.get());
        auditService.registerEvent("RESERVA_ELIMINADA", id, "Se eliminó la reserva");
        return true;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation updateStatus(Long id, String newStatus) {
        reservationAccess.requireStaff();
        if (newStatus == null || newStatus.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado es obligatorio");
        }
        Reservation reservation = lockReservation(id);
        String currentStatus = reservation.getStatus();
        if (currentStatus == null
                || !TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La transición de estado solicitada no está permitida");
        }

        Unit unit = lockUnit(reservation.getUnitId());
        if (BLOCKING_STATUSES.contains(newStatus)) {
            validateDetails(reservation);
            if ("CONFIRMADA".equals(newStatus)) {
                requireAvailable(unit);
            }
            requireNoOverlap(reservation, reservation.getId());
        }

        reservation.setStatus(newStatus);
        Reservation saved = reservationRepository.save(reservation);
        String action = switch (newStatus) {
            case "CONFIRMADA" -> "RESERVA_CONFIRMADA";
            case "CANCELADA" -> "RESERVA_CANCELADA";
            case "CHECKIN_PENDIENTE" -> "CHECKIN_PENDIENTE";
            case "EN_ESTADÍA" -> "CHECKIN";
            case "CHECKOUT" -> "CHECKOUT";
            default -> throw new IllegalStateException("Transición sin evento de auditoría");
        };
        reservationProducer.sendReservationStatusChanged(saved);
        auditService.registerEvent(action, saved.getId(),
                "La reserva cambió de " + currentStatus + " a " + newStatus);
        return saved;
    }

    private void validateDetails(Reservation reservation) {
        if (reservation == null || reservation.getGuestName() == null
                || reservation.getGuestName().isBlank()
                || reservation.getGuestName().trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre del huésped es obligatorio y admite hasta 255 caracteres");
        }
        if (reservation.getUnitId() == null || reservation.getUnitId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El identificador de unidad debe ser positivo");
        }
        if (reservation.getCheckIn() == null || reservation.getCheckOut() == null
                || !reservation.getCheckOut().isAfter(reservation.getCheckIn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de salida debe ser posterior a la fecha de entrada");
        }
    }

    private Reservation lockReservation(Long id) {
        return reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reserva no encontrada"));
    }

    private Unit lockUnit(Long id) {
        return unitRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unidad no encontrada"));
    }

    private Unit lockUnits(Long currentUnitId, Long targetUnitId) {
        Unit target = null;
        for (Long unitId : Stream.of(currentUnitId, targetUnitId).distinct().sorted().toList()) {
            Unit unit = lockUnit(unitId);
            if (unitId.equals(targetUnitId)) {
                target = unit;
            }
        }
        return target;
    }

    private void requireAvailable(Unit unit) {
        if (!Boolean.TRUE.equals(unit.getAvailable())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La unidad está deshabilitada para nuevas reservas");
        }
    }

    private void requireNoOverlap(Reservation reservation, Long excludedId) {
        if (reservationRepository.existsOverlappingReservation(reservation.getUnitId(),
                BLOCKING_STATUSES, reservation.getCheckIn(), reservation.getCheckOut(), excludedId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La unidad ya tiene una reserva que ocupa esas fechas");
        }
    }
}

