package com.example.AndesStay.service;

import com.example.AndesStay.entity.AuditEvent;
import com.example.AndesStay.repository.AuditEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import java.util.List;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final KafkaProducer kafkaProducer;
    private final Clock clock;

    public AuditService(
            AuditEventRepository auditEventRepository,
            KafkaProducer kafkaProducer, Clock clock) {

        this.auditEventRepository = auditEventRepository;
        this.kafkaProducer = kafkaProducer;
        this.clock = clock;
    }

    @Transactional
    public AuditEvent registerEvent(
            String action,
            Long reservationId,
            String description) {

        AuditEvent event = new AuditEvent();

        event.setAction(action);
        event.setReservationId(reservationId);
        event.setDescription(description);
        event.setEventDate(LocalDateTime.now(clock));

        // Obtener usuario desde el JWT de Azure AD
        event.setUser(obtenerUsuarioActual());

        AuditEvent savedEvent =
                auditEventRepository.save(event);

        String kafkaMessage =
                "AUDITORIA: "
                + "ID=" + savedEvent.getId()
                + ", Acción=" + savedEvent.getAction()
                + ", Reserva=" + savedEvent.getReservationId()
                + ", Usuario=" + savedEvent.getUser()
                + ", Descripción=" + savedEvent.getDescription()
                + ", Fecha=" + savedEvent.getEventDate();

        kafkaProducer.sendReservationEvent(kafkaMessage);

        return savedEvent;
    }

    private String obtenerUsuarioActual() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {

            String usuario =
                    jwtAuth.getToken().getClaimAsString(
                            "preferred_username"
                    );

            if (usuario != null && !usuario.isBlank()) {
                return usuario;
            }

            usuario =
                    jwtAuth.getToken().getClaimAsString(
                            "email"
                    );

            if (usuario != null && !usuario.isBlank()) {
                return usuario;
            }

            usuario =
                    jwtAuth.getToken().getClaimAsString(
                            "upn"
                    );

            if (usuario != null && !usuario.isBlank()) {
                return usuario;
            }

            return jwtAuth.getToken().getSubject();
        }

        return "SISTEMA";
    }

    public List<AuditEvent> getAllEvents() {
        return getFilteredEvents(null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> getFilteredEvents(
            String user,
            String action,
            LocalDateTime inicio,
            LocalDateTime fin) {

        if (inicio != null && fin != null && inicio.isAfter(fin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio no puede ser posterior a la fecha de fin");
        }

        return auditEventRepository.findFilteredEvents(
                normalize(user), normalize(action), inicio, fin);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> getFilteredEventsPage(String user, String action,
            LocalDateTime inicio, LocalDateTime fin, int page, int size) {
        if (inicio != null && fin != null && inicio.isAfter(fin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio no puede ser posterior a la fecha de fin");
        }
        return auditEventRepository.findFilteredEventsPage(normalize(user), normalize(action), inicio, fin,
                Pagination.request(page, size, Sort.unsorted()));
    }
}
