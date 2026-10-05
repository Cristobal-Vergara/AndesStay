package com.example.AndesStay.service;

import com.example.AndesStay.dto.ReservationNotificationResponse;
import com.example.AndesStay.entity.ReservationNotification;
import com.example.AndesStay.repository.ReservationNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;

@Service
public class ReservationNotificationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationNotificationService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ReservationNotificationRepository repository;
    private final ReservationAccess access;
    private final Clock clock;

    public ReservationNotificationService(ReservationNotificationRepository repository, ReservationAccess access, Clock clock) {
        this.repository = repository; this.access = access; this.clock = clock;
    }

    @Transactional
    public void receive(String payload, boolean guest) {
        // El productor original enviaba texto plano; no convertirlo en un aviso inventado.
        if (payload.startsWith("Reserva creada: ")) {
            log.info("Entrega de texto anterior conservada en inbox");
            return;
        }
        var envelope = JSON.readTree(payload);
        // Las entregas antiguas sin un tipo de reserva se mantienen registradas en inbox.
        ReservationEventType type;
        try { type = ReservationEventType.valueOf(envelope.path("type").asString()); }
        catch (IllegalArgumentException | NullPointerException ignored) {
            log.info("Entrega anterior sin un evento de reserva compatible");
            return;
        }
        var data = envelope.path("data");
        if (guest && (type == ReservationEventType.RESERVATION_CREATED
                || !data.path("guestAssigned").asBoolean())) return;
        String recipient = guest ? data.path("ownerId").asString() : null;
        if (guest && (recipient == null || !recipient.matches(
                "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}:[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))) {
            throw new IllegalArgumentException("El aviso de huésped necesita una identidad válida");
        }
        long reservationId = data.path("reservationId").asLong();
        long unitId = data.path("unitId").asLong();
        if (reservationId <= 0 || unitId <= 0) throw new IllegalArgumentException("El aviso necesita una reserva y una unidad");
        String timestamp = envelope.path("occurredAt").asString();
        Instant occurredAt = timestamp == null || timestamp.isBlank() ? clock.instant() : Instant.parse(timestamp);
        repository.save(new ReservationNotification(guest ? "GUEST" : "RECEPTION", recipient,
                reservationId, unitId, type.status(), LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)));
    }

    @Transactional(readOnly = true)
    public Page<ReservationNotificationResponse> list(int page, int size) {
        var request = Pagination.request(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        boolean staff = access.isStaff();
        var notifications = staff ? repository.findByAudience("RECEPTION", request)
                : repository.findByAudienceAndRecipientId("GUEST", access.currentOwnerId(), request);
        return notifications.map(notice -> new ReservationNotificationResponse(notice.getId(),
                notice.getReservationId(), notice.getUnitId(), notice.getStatus(), title(notice, staff),
                notice.getOccurredAt().toInstant(ZoneOffset.UTC)));
    }

    private String title(ReservationNotification notice, boolean staff) {
        String subject = staff ? "Reserva #" + notice.getReservationId() : "Tu reserva #" + notice.getReservationId();
        return subject + switch (notice.getStatus()) {
            case "CREADA" -> " recibida";
            case "CONFIRMADA" -> " confirmada";
            case "CANCELADA" -> " cancelada";
            default -> throw new IllegalStateException("Estado de aviso desconocido");
        };
    }
}
