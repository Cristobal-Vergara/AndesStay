package com.example.AndesStay.service;

import com.example.AndesStay.entity.AuditEvent;
import com.example.AndesStay.repository.AuditEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final KafkaProducer kafkaProducer;

    public AuditService(
            AuditEventRepository auditEventRepository,
            KafkaProducer kafkaProducer) {

        this.auditEventRepository = auditEventRepository;
        this.kafkaProducer = kafkaProducer;
    }

    public AuditEvent registerEvent(
            String action,
            Long reservationId,
            String description) {

        AuditEvent event = new AuditEvent();

        event.setAction(action);
        event.setReservationId(reservationId);
        event.setDescription(description);
        event.setEventDate(LocalDateTime.now());

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

        return auditEventRepository.findAll();
    }

    public List<AuditEvent> getFilteredEvents(
            String user,
            String action,
            LocalDateTime inicio,
            LocalDateTime fin) {

        if (user != null
                && !user.isBlank()
                && action != null
                && !action.isBlank()
                && inicio != null
                && fin != null) {

            return auditEventRepository
                    .findByUserAndActionAndEventDateBetween(
                            user,
                            action,
                            inicio,
                            fin
                    );
        }

        if (user != null && !user.isBlank()
                && action != null && !action.isBlank()) {

            return auditEventRepository
                    .findByUserAndAction(
                            user,
                            action
                    );
        }

        if (user != null && !user.isBlank()
                && inicio != null && fin != null) {

            return auditEventRepository
                    .findByUserAndEventDateBetween(
                            user,
                            inicio,
                            fin
                    );
        }

        if (user != null && !user.isBlank()) {

            return auditEventRepository
                    .findByUser(user);
        }

        if (action != null && !action.isBlank()
                && inicio != null && fin != null) {

            return auditEventRepository
                    .findByActionAndEventDateBetween(
                            action,
                            inicio,
                            fin
                    );
        }

        if (action != null && !action.isBlank()) {

            return auditEventRepository
                    .findByAction(action);
        }

        if (inicio != null && fin != null) {

            return auditEventRepository
                    .findByEventDateBetween(
                            inicio,
                            fin
                    );
        }

        return auditEventRepository.findAll();
    }
}