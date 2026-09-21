package com.example.AndesStay.service;

import com.example.AndesStay.entity.Reservation;
import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.repository.ReservationRepository;
import com.example.AndesStay.repository.UnitRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UnitRepository unitRepository;
    private final ReservationProducer reservationProducer;
    private final KafkaProducer kafkaProducer;
    private final AuditService auditService;

    public ReservationService(
            ReservationRepository reservationRepository,
            UnitRepository unitRepository,
            ReservationProducer reservationProducer,
            KafkaProducer kafkaProducer,
            AuditService auditService) {

        this.reservationRepository = reservationRepository;
        this.unitRepository = unitRepository;
        this.reservationProducer = reservationProducer;
        this.kafkaProducer = kafkaProducer;
        this.auditService = auditService;
    }

    public Reservation createReservation(Reservation reservation) {

        // Validar nombre del huésped
        if (reservation.getGuestName() == null
                || reservation.getGuestName().isBlank()) {
            return null;
        }

        // Validar unitId
        if (reservation.getUnitId() == null) {
            return null;
        }

        // Validar que la unidad exista
        Optional<Unit> unit =
                unitRepository.findById(reservation.getUnitId());

        if (unit.isEmpty()) {
            return null;
        }

        // Validar fecha de entrada
        if (reservation.getCheckIn() == null) {
            return null;
        }

        // Validar fecha de salida
        if (reservation.getCheckOut() == null) {
            return null;
        }

        // La fecha de salida debe ser posterior a la fecha de entrada
        if (!reservation.getCheckOut().isAfter(reservation.getCheckIn())) {
            return null;
        }

        // Estado inicial
        if (reservation.getStatus() == null
                || reservation.getStatus().isBlank()) {
            reservation.setStatus("CREADA");
        }

        // Guardar reserva
        Reservation savedReservation =
                reservationRepository.save(reservation);

        // RabbitMQ
        reservationProducer.sendReservationCreated(savedReservation);

        // Kafka
        String kafkaMessage =
                "Reserva creada: "
                + "ID=" + savedReservation.getId()
                + ", Huésped=" + savedReservation.getGuestName()
                + ", Unidad=" + savedReservation.getUnitId()
                + ", CheckIn=" + savedReservation.getCheckIn()
                + ", CheckOut=" + savedReservation.getCheckOut()
                + ", Estado=" + savedReservation.getStatus();

        kafkaProducer.sendReservationEvent(kafkaMessage);

        // Auditoría
        auditService.registerEvent(
                "RESERVA_CREADA",
                savedReservation.getId(),
                "Se creó la reserva para el huésped "
                        + savedReservation.getGuestName()
        );

        return savedReservation;
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public Optional<Reservation> getReservationById(Long id) {
        return reservationRepository.findById(id);
    }

    public Reservation updateReservation(
            Long id,
            Reservation reservation) {

        Optional<Reservation> existingReservation =
                reservationRepository.findById(id);

        if (existingReservation.isEmpty()) {
            return null;
        }

        Reservation current = existingReservation.get();

        current.setGuestName(reservation.getGuestName());
        current.setUnitId(reservation.getUnitId());
        current.setCheckIn(reservation.getCheckIn());
        current.setCheckOut(reservation.getCheckOut());

        return reservationRepository.save(current);
    }

    public boolean deleteReservation(Long id) {

        if (!reservationRepository.existsById(id)) {
            return false;
        }

        reservationRepository.deleteById(id);

        return true;
    }

    public Reservation updateStatus(Long id, String newStatus) {

        Optional<Reservation> existingReservation =
                reservationRepository.findById(id);

        if (existingReservation.isEmpty()) {
            return null;
        }

        Reservation reservation = existingReservation.get();

        String currentStatus = reservation.getStatus();

        // CREADA → CONFIRMADA
        if (currentStatus.equals("CREADA")
                && newStatus.equals("CONFIRMADA")) {

            Optional<Unit> unit =
                    unitRepository.findById(reservation.getUnitId());

            if (unit.isEmpty()) {
                return null;
            }

            // Buscar reservas confirmadas de la misma unidad
            List<Reservation> confirmedReservations =
                    reservationRepository.findByUnitIdAndStatus(
                            reservation.getUnitId(),
                            "CONFIRMADA"
                    );

            // Comprobar si las fechas se cruzan
            for (Reservation existing : confirmedReservations) {

                boolean datesOverlap =
                        reservation.getCheckIn().isBefore(existing.getCheckOut())
                        && reservation.getCheckOut().isAfter(existing.getCheckIn());

                if (datesOverlap) {
                    return null;
                }
            }

            reservation.setStatus(newStatus);

            Reservation savedReservation =
                    reservationRepository.save(reservation);

            // Auditoría
            auditService.registerEvent(
                    "RESERVA_CONFIRMADA",
                    savedReservation.getId(),
                    "La reserva cambió de CREADA a CONFIRMADA"
            );

            return savedReservation;
        }

        // CREADA → CANCELADA
        if (currentStatus.equals("CREADA")
                && newStatus.equals("CANCELADA")) {

            reservation.setStatus(newStatus);

            Reservation savedReservation =
                    reservationRepository.save(reservation);

            // Auditoría
            auditService.registerEvent(
                    "RESERVA_CANCELADA",
                    savedReservation.getId(),
                    "La reserva cambió de CREADA a CANCELADA"
            );

            return savedReservation;
        }

        // CONFIRMADA → CHECKIN_PENDIENTE
        // CONFIRMADA → CANCELADA
        if (currentStatus.equals("CONFIRMADA")
                && (newStatus.equals("CHECKIN_PENDIENTE")
                || newStatus.equals("CANCELADA"))) {

            reservation.setStatus(newStatus);

            Reservation savedReservation =
                    reservationRepository.save(reservation);

            String action;

            if (newStatus.equals("CHECKIN_PENDIENTE")) {
                action = "CHECKIN_PENDIENTE";
            } else {
                action = "RESERVA_CANCELADA";
            }

            auditService.registerEvent(
                    action,
                    savedReservation.getId(),
                    "La reserva cambió de "
                            + currentStatus
                            + " a "
                            + newStatus
            );

            return savedReservation;
        }

        // CHECKIN_PENDIENTE → EN_ESTADÍA
        if (currentStatus.equals("CHECKIN_PENDIENTE")
                && newStatus.equals("EN_ESTADÍA")) {

            reservation.setStatus(newStatus);

            Reservation savedReservation =
                    reservationRepository.save(reservation);

            // Auditoría
            auditService.registerEvent(
                    "CHECKIN",
                    savedReservation.getId(),
                    "La reserva pasó de CHECKIN_PENDIENTE a EN_ESTADÍA"
            );

            return savedReservation;
        }

        // EN_ESTADÍA → CHECKOUT
        if (currentStatus.equals("EN_ESTADÍA")
                && newStatus.equals("CHECKOUT")) {

            reservation.setStatus(newStatus);

            Reservation savedReservation =
                    reservationRepository.save(reservation);

            // Auditoría
            auditService.registerEvent(
                    "CHECKOUT",
                    savedReservation.getId(),
                    "La reserva pasó de EN_ESTADÍA a CHECKOUT"
            );

            return savedReservation;
        }

        return null;
    }
}

