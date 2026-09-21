package com.example.AndesStay.service;

import com.example.AndesStay.entity.AuditEvent;
import com.example.AndesStay.entity.Reservation;
import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.repository.AuditEventRepository;
import com.example.AndesStay.repository.ReservationRepository;
import com.example.AndesStay.repository.UnitRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final ReservationRepository reservationRepository;
    private final UnitRepository unitRepository;
    private final AuditEventRepository auditEventRepository;

    public ReportService(
            ReservationRepository reservationRepository,
            UnitRepository unitRepository,
            AuditEventRepository auditEventRepository) {

        this.reservationRepository = reservationRepository;
        this.unitRepository = unitRepository;
        this.auditEventRepository = auditEventRepository;
    }

    public Map<String, Object> getReservationKPIs() {

        Map<String, Object> kpis = new LinkedHashMap<>();

        // Total de reservas
        kpis.put(
                "totalReservas",
                reservationRepository.count()
        );

        // Reservas por estado
        kpis.put(
                "creadas",
                reservationRepository.countByStatus("CREADA")
        );

        kpis.put(
                "confirmadas",
                reservationRepository.countByStatus("CONFIRMADA")
        );

        kpis.put(
                "canceladas",
                reservationRepository.countByStatus("CANCELADA")
        );

        kpis.put(
                "checkinPendiente",
                reservationRepository.countByStatus("CHECKIN_PENDIENTE")
        );

        kpis.put(
                "enEstadia",
                reservationRepository.countByStatus("EN_ESTADÍA")
        );

        kpis.put(
                "checkout",
                reservationRepository.countByStatus("CHECKOUT")
        );

        // Ocupación activa
        kpis.put(
                "ocupacionActiva",
                reservationRepository.countByStatus("EN_ESTADÍA")
        );

        // Reservas por unidad
        Map<Long, Long> reservasPorUnidad = new LinkedHashMap<>();

        List<Unit> units = unitRepository.findAll();

        for (Unit unit : units) {

            reservasPorUnidad.put(
                    unit.getId(),
                    reservationRepository.countByUnitId(unit.getId())
            );
        }

        kpis.put(
                "reservasPorUnidad",
                reservasPorUnidad
        );

        // Tiempo de ciclo promedio
        long totalMinutos = 0;
        int reservasConCiclo = 0;

        List<Reservation> reservations =
                reservationRepository.findAll();

        for (Reservation reservation : reservations) {

            List<AuditEvent> events =
                    auditEventRepository
                            .findByReservationIdOrderByEventDateAsc(
                                    reservation.getId()
                            );

            LocalDateTime inicio = null;
            LocalDateTime fin = null;

            for (AuditEvent event : events) {

                if ("RESERVA_CREADA".equals(event.getAction())) {
                    inicio = event.getEventDate();
                }

                if ("RESERVA_CONFIRMADA".equals(event.getAction())) {
                    fin = event.getEventDate();
                    break;
                }
            }

            if (inicio != null && fin != null) {

                long minutos =
                        Duration.between(inicio, fin).toMinutes();

                totalMinutos += minutos;
                reservasConCiclo++;
            }
        }

        double promedioMinutos = 0;

        if (reservasConCiclo > 0) {
            promedioMinutos =
                    (double) totalMinutos / reservasConCiclo;
        }

        kpis.put(
                "tiempoCicloPromedioMinutos",
                promedioMinutos
        );

        // Reservas por hora
        Map<Integer, Long> reservasPorHora = new LinkedHashMap<>();

        List<AuditEvent> eventosCreacion =
                auditEventRepository.findByAction("RESERVA_CREADA");

        for (AuditEvent event : eventosCreacion) {

            int hora = event.getEventDate().getHour();

            reservasPorHora.put(
                    hora,
                    reservasPorHora.getOrDefault(hora, 0L) + 1
            );
        }

        kpis.put(
                "reservasPorHora",
                reservasPorHora
        );

        return kpis;
    }
}