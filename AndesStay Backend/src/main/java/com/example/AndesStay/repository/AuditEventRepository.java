package com.example.AndesStay.repository;

import com.example.AndesStay.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditEventRepository
        extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByReservationIdOrderByEventDateAsc(
            Long reservationId
    );

    List<AuditEvent> findByAction(String action);

    @Query("""
            select e from AuditEvent e
            where (:user is null or e.user = :user)
              and (:action is null or e.action = :action)
              and (:inicio is null or e.eventDate >= :inicio)
              and (:fin is null or e.eventDate <= :fin)
            order by e.eventDate desc, e.id desc
            """)
    Page<AuditEvent> findFilteredEventsPage(
            @Param("user") String user, @Param("action") String action,
            @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin, Pageable pageable);

    @Query("select e from AuditEvent e where e.action in ('RESERVA_CREADA', 'RESERVA_CONFIRMADA') order by e.reservationId, e.eventDate, e.id")
    List<AuditEvent> findCycleEvents();

    @Query("""
            select e from AuditEvent e
            where (:user is null or e.user = :user)
              and (:action is null or e.action = :action)
              and (:inicio is null or e.eventDate >= :inicio)
              and (:fin is null or e.eventDate <= :fin)
            order by e.eventDate desc, e.id desc
            """)
    List<AuditEvent> findFilteredEvents(
            @Param("user") String user,
            @Param("action") String action,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );
}
