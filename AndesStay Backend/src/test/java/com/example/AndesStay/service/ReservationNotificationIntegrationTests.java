package com.example.AndesStay.service;

import com.example.AndesStay.entity.*;
import com.example.AndesStay.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.*;
import org.springframework.amqp.core.Message;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:notices;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
    "spring.jpa.show-sql=false"
}, showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationService.class, ReservationAccess.class, AuditService.class, KafkaProducer.class,
    ReservationProducer.class, OutboxService.class, InboxService.class,
    ReservationNotificationService.class, ReservationConsumer.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ReservationNotificationIntegrationTests {
    @Autowired ReservationService reservations;
    @Autowired ReservationNotificationService notices;
    @Autowired ReservationConsumer consumer;
    @Autowired ReservationNotificationRepository noticeRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired UnitRepository units;
    @Autowired AuditEventRepository audits;
    @Autowired OutboxRepository outbox;
    @Autowired InboxService inbox;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    @MockitoSpyBean OutboxService enqueue;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(WorkflowIntegrationTests.NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Santiago"));
        jdbc.update("delete from consumed_events");
        noticeRepository.deleteAll(); outbox.deleteAll(); audits.deleteAll();
        reservationRepository.deleteAll(); units.deleteAll();
        authenticate("Huesped", WorkflowIntegrationTests.TENANT);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void realReservationActionsCreateThreeReceptionNoticesAndTwoOwnGuestNotices() {
        var reservation = create();
        var created = rabbitEvents().get(0);
        consumer.receiveReservation(message(created));
        assertEquals(0, notices.list(0, 5).getTotalElements());
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        reservations.updateStatus(reservation.getId(), "CANCELADA");
        for (var event : rabbitEvents()) {
            consumer.receiveReservation(message(event));
            if (!event.getId().equals(created.getId())) consumer.receiveGuestNotification(message(event));
        }
        assertEquals(3, notices.list(0, 5).getTotalElements());
        assertEquals(Set.of("CREADA","CONFIRMADA","CANCELADA"),
            new HashSet<>(notices.list(0, 5).map(n -> n.status()).getContent()));
        authenticate("Huesped", WorkflowIntegrationTests.TENANT);
        assertEquals(2, notices.list(0, 5).getTotalElements());
        assertTrue(notices.list(0, 5).stream().allMatch(n -> n.reservationId().equals(reservation.getId())));
        assertTrue(notices.list(0, 5).getContent().get(0).title().startsWith("Tu reserva"));
    }

    @Test void redeliveryIsIndependentPerConsumerAndDoesNotRepeatEitherNotice() {
        var reservation = create();
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        var event = rabbitEvents().stream().filter(e -> e.getPayload().contains("RESERVATION_CONFIRMED")).findFirst().orElseThrow();
        for (int i=0; i<3; i++) {
            consumer.receiveReservation(message(event));
            consumer.receiveGuestNotification(message(event));
        }
        assertEquals(2, noticeRepository.count());
        assertEquals(2L, jdbc.queryForObject("select count(*) from consumed_events where event_id=?", Long.class, event.getId()));
    }

    @Test void recipientIsTakenFromEventAndListIsRestrictedByTenantAndObjectId() {
        var reservation = create();
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        consumer.receiveGuestNotification(message(rabbitEvents().stream()
            .filter(e -> e.getPayload().contains("RESERVATION_CONFIRMED")).findFirst().orElseThrow()));
        authenticate("Huesped", "44444444-4444-4444-4444-444444444444");
        assertEquals(0, notices.list(0, 5).getTotalElements());
        WorkflowIntegrationTests.authenticate("Huesped", WorkflowIntegrationTests.TENANT, WorkflowIntegrationTests.STAFF);
        assertEquals(0, notices.list(0, 5).getTotalElements());
        authenticate("Huesped", WorkflowIntegrationTests.TENANT);
        assertEquals(1, notices.list(0, 1).getTotalElements());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> notices.list(-1, 5));
        authenticate("Auditor", WorkflowIntegrationTests.TENANT);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> notices.list(0, 5));
    }

    @Test void receptionCreatedReservationDoesNotNotifyStaffAsIfTheyWereGuest() {
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        var reservation = create();
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        var event = rabbitEvents().stream().filter(e -> e.getPayload().contains("RESERVATION_CONFIRMED")).findFirst().orElseThrow();
        consumer.receiveReservation(message(event));
        consumer.receiveGuestNotification(message(event));
        assertEquals(1, noticeRepository.count());
        assertEquals(1, notices.list(0, 5).getTotalElements());
    }

    @Test void failureAfterSavingNoticeRollsBackNoticeAndInboxReceiptTogether() {
        var event = rabbitEventsAfterCreate();
        assertThrows(IllegalStateException.class, () -> inbox.process("RABBITMQ", event.getPayload(), () -> {
            notices.receive(event.getPayload(), false);
            throw new IllegalStateException("simulated failure");
        }));
        assertEquals(0, noticeRepository.count());
        assertEquals(0L, jdbc.queryForObject("select count(*) from consumed_events", Long.class));
        consumer.receiveReservation(message(event));
        assertEquals(1, noticeRepository.count());
    }

    @Test void failedStatusEventEnqueueRollsBackStatusAndAudit() {
        var reservation = create();
        long initialAudit = audits.count();
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        OutboxService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(enqueue);
        doThrow(new IllegalStateException("storage unavailable")).when(target)
            .enqueue(eq("RABBITMQ"), eq("RESERVATION_CONFIRMED"), any());
        assertThrows(IllegalStateException.class, () -> reservations.updateStatus(reservation.getId(), "CONFIRMADA"));
        assertEquals("CREADA", reservationRepository.findById(reservation.getId()).orElseThrow().getStatus());
        assertEquals(initialAudit, audits.count());
        assertEquals(3, outbox.count());
    }

    @Test void oldCreatedEnvelopeWithoutIdentityStillProducesReceptionNotice() {
        String payload = "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"RESERVATION_CREATED\","
            + "\"data\":{\"reservationId\":12,\"unitId\":3,\"status\":\"CREADA\"}}";
        consumer.receiveReservation(new Message(payload.getBytes(StandardCharsets.UTF_8)));
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        assertEquals(WorkflowIntegrationTests.NOW, notices.list(0, 5).getContent().get(0).occurredAt());
    }

    @Test void eventTimeOrdersNoticesEvenWhenDeliveryOrderIsReversed() {
        var reservation = create();
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        when(clock.instant()).thenReturn(WorkflowIntegrationTests.NOW.plusSeconds(60));
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        when(clock.instant()).thenReturn(WorkflowIntegrationTests.NOW.plusSeconds(120));
        reservations.updateStatus(reservation.getId(), "CANCELADA");
        var events = rabbitEvents();
        consumer.receiveGuestNotification(message(events.stream()
            .filter(e -> e.getPayload().contains("RESERVATION_CANCELLED")).findFirst().orElseThrow()));
        consumer.receiveGuestNotification(message(events.stream()
            .filter(e -> e.getPayload().contains("RESERVATION_CONFIRMED")).findFirst().orElseThrow()));
        authenticate("Huesped", WorkflowIntegrationTests.TENANT);
        assertEquals(List.of("CANCELADA", "CONFIRMADA"), notices.list(0,5).map(n -> n.status()).getContent());
    }

    @Test void originalPlainTextDeliveryIsAcknowledgedAndDeduplicatedWithoutInventingNotice() {
        var legacy = new Message("Reserva creada: ID=12, Huésped=Ana, Unidad=3".getBytes(StandardCharsets.UTF_8));
        consumer.receiveReservation(legacy);
        consumer.receiveReservation(legacy);
        assertEquals(0, noticeRepository.count());
        assertEquals(1L, jdbc.queryForObject("select count(*) from consumed_events", Long.class));
    }

    private Reservation create() {
        var unit = units.saveAndFlush(new Unit(null, "Cabaña", "CABANA", 2, BigDecimal.TEN, true, "Prueba"));
        return reservations.createReservation(new Reservation(null, "Ana", unit.getId(),
            LocalDate.of(2027,1,1), LocalDate.of(2027,1,3), "CREADA"));
    }
    private OutboxEvent rabbitEventsAfterCreate() { create(); return rabbitEvents().get(0); }
    private List<OutboxEvent> rabbitEvents() {
        return outbox.findAll().stream().filter(e -> e.getDestination().equals("RABBITMQ")).toList();
    }
    private Message message(OutboxEvent event) { return new Message(event.getPayload().getBytes(StandardCharsets.UTF_8)); }
    private void authenticate(String role, String tenant) {
        WorkflowIntegrationTests.authenticate(role, tenant, WorkflowIntegrationTests.GUEST);
    }
}
