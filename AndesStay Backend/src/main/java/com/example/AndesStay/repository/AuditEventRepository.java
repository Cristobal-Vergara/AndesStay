package com.example.AndesStay.repository;

import com.example.AndesStay.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditEventRepository
        extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByReservationIdOrderByEventDateAsc(
            Long reservationId
    );

    List<AuditEvent> findByAction(String action);

    List<AuditEvent> findByEventDateBetween(
            LocalDateTime inicio,
            LocalDateTime fin
    );

    List<AuditEvent> findByActionAndEventDateBetween(
            String action,
            LocalDateTime inicio,
            LocalDateTime fin
    );

    List<AuditEvent> findByUser(String user);

    List<AuditEvent> findByUserAndAction(
            String user,
            String action
    );

    List<AuditEvent> findByUserAndEventDateBetween(
            String user,
            LocalDateTime inicio,
            LocalDateTime fin
    );

    List<AuditEvent> findByUserAndActionAndEventDateBetween(
            String user,
            String action,
            LocalDateTime inicio,
            LocalDateTime fin
    );
}