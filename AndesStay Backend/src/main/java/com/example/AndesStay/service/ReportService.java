package com.example.AndesStay.service;

import com.example.AndesStay.entity.AuditEvent;
import com.example.AndesStay.entity.Reservation;
import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.repository.AuditEventRepository;
import com.example.AndesStay.repository.ReservationRepository;
import com.example.AndesStay.repository.UnitRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReportService {
    private final ReservationRepository reservations;
    private final UnitRepository units;
    private final AuditEventRepository audits;
    private final ReservationAccess access;
    private final Clock clock;

    public ReportService(ReservationRepository reservations, UnitRepository units,
            AuditEventRepository audits, ReservationAccess access, Clock clock) {
        this.reservations = reservations; this.units = units; this.audits = audits;
        this.access = access; this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> getReservationKPIs() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Long> counts = new HashMap<>();
        reservations.countGroupedByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        result.put("totalReservas", counts.values().stream().mapToLong(Long::longValue).sum());
        result.put("creadas", counts.getOrDefault("CREADA", 0L));
        result.put("confirmadas", counts.getOrDefault("CONFIRMADA", 0L));
        result.put("canceladas", counts.getOrDefault("CANCELADA", 0L));
        result.put("checkinPendiente", counts.getOrDefault("CHECKIN_PENDIENTE", 0L));
        result.put("enEstadia", counts.getOrDefault("EN_ESTADÍA", 0L));
        result.put("checkout", counts.getOrDefault("CHECKOUT", 0L));

        List<Unit> catalog = units.findAll(Sort.by("id"));
        long occupied = reservations.countOccupiedUnits();
        result.put("totalUnidades", catalog.size());
        result.put("unidadesHabilitadas", catalog.stream().filter(unit -> Boolean.TRUE.equals(unit.getAvailable())).count());
        result.put("ocupacionActiva", occupied);
        result.put("ocupacionPorcentaje", catalog.isEmpty() ? 0.0 : occupied * 100.0 / catalog.size());

        Map<Long, Long> byUnit = new LinkedHashMap<>();
        catalog.forEach(unit -> byUnit.put(unit.getId(), 0L));
        reservations.countGroupedByUnit().forEach(row -> byUnit.put(row.getUnitId(), row.getTotal()));
        result.put("reservasPorUnidad", byUnit);

        // Una sola lectura del historial sustituye las consultas por cada reserva.
        List<AuditEvent> history = audits.findCycleEvents();
        Map<Long, LocalDateTime> starts = new HashMap<>();
        Set<Long> completed = new HashSet<>();
        Map<Integer, Long> byHour = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) byHour.put(hour, 0L);
        double totalMinutes = 0;
        for (AuditEvent event : history) {
            Long id = event.getReservationId();
            if ("RESERVA_CREADA".equals(event.getAction()) && !starts.containsKey(id)) {
                starts.put(id, event.getEventDate());
                byHour.merge(event.getEventDate().getHour(), 1L, Long::sum);
            } else if ("RESERVA_CONFIRMADA".equals(event.getAction()) && starts.containsKey(id)
                    && !completed.contains(id) && !event.getEventDate().isBefore(starts.get(id))) {
                totalMinutes += Duration.between(starts.get(id), event.getEventDate()).toSeconds() / 60.0;
                completed.add(id);
            }
        }
        result.put("reservasConCiclo", completed.size());
        result.put("tiempoCicloPromedioMinutos", completed.isEmpty() ? 0.0 : totalMinutes / completed.size());
        result.put("reservasPorHora", byHour);
        result.put("timeZone", clock.getZone().getId());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDailyOperations(LocalDate requestedDate) {
        access.requireStaff();
        LocalDate date = requestedDate == null ? LocalDate.now(clock) : requestedDate;
        List<Reservation> candidates = reservations.findOperationsOn(date);
        Comparator<Reservation> order = Comparator.comparing(Reservation::getUnitId).thenComparing(Reservation::getId);
        List<Reservation> arrivals = candidates.stream().filter(r -> date.equals(r.getCheckIn())
                && Set.of("CONFIRMADA", "CHECKIN_PENDIENTE").contains(r.getStatus())).sorted(order).toList();
        List<Reservation> departures = candidates.stream().filter(r -> date.equals(r.getCheckOut())
                && "EN_ESTADÍA".equals(r.getStatus())).sorted(order).toList();
        return Map.of("fecha", date.toString(), "timeZone", clock.getZone().getId(),
                "llegadas", arrivals, "salidas", departures);
    }
}
