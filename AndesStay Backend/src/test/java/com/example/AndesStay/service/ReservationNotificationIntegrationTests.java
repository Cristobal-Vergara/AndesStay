package com.example.AndesStay.service;

import com.example.AndesStay.entity.*;
import com.example.AndesStay.config.RabbitDeliveryConfig;
import com.rabbitmq.client.Channel;
import com.example.AndesStay.messaging.notifications.GuestNotificationConsumer;
import com.example.AndesStay.messaging.notifications.ReceptionNotificationConsumer;
import com.example.AndesStay.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
    ReservationNotificationService.class, ReceptionNotificationConsumer.class, GuestNotificationConsumer.class,
    RabbitDeliveryConfig.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ReservationNotificationIntegrationTests {
    @Autowired ReservationService reservations;
    @Autowired ReservationNotificationService notices;
    @Autowired ReceptionNotificationConsumer receptionConsumer;
    @Autowired GuestNotificationConsumer guestConsumer;
    @Autowired ReservationNotificationRepository noticeRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired UnitRepository units;
    @Autowired AuditEventRepository audits;
    @Autowired OutboxRepository outbox;
    @Autowired InboxService inbox;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    @MockitoSpyBean OutboxService enqueue;
    private Channel channel;

    @BeforeEach void setup() {
        channel = mock(Channel.class);
        when(clock.instant()).thenReturn(WorkflowIntegrationTests.NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Santiago"));
        jdbc.update("delete from consumed_events");
        noticeRepository.deleteAll(); outbox.deleteAll(); audits.deleteAll();
        reservationRepository.deleteAll(); units.deleteAll();
        authenticate("Huesped", WorkflowIntegrationTests.TENANT);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void realReservationActionsCreateThreeReceptionNoticesAndTwoOwnGuestNotices() throws java.io.IOException {
        var reservation = create();
        var created = rabbitEvents().get(0);
        deliverReception(message(created));
        assertEquals(0, notices.list(0, 5).getTotalElements());
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        reservations.updateStatus(reservation.getId(), "CANCELADA");
        for (var event : rabbitEvents()) {
            deliverReception(message(event));
            if (!event.getId().equals(created.getId())) deliverGuest(message(event));
        }
        assertEquals(3, notices.list(0, 5).getTotalElements());
        assertEquals(Set.of("CREADA","CONFIRMADA","CANCELADA"),
            new HashSet<>(notices.list(0, 5).map(n -> n.status()).getContent()));
        authenticate("Huesped", WorkflowIntegrationTests.TENANT);
        assertEquals(2, notices.list(0, 5).getTotalElements());
        assertTrue(notices.list(0, 5).stream().allMatch(n -> n.reservationId().equals(reservation.getId())));
        assertTrue(notices.list(0, 5).getContent().get(0).title().startsWith("Tu reserva"));
    }

    @Test void redeliveryIsIndependentPerConsumerAndDoesNotRepeatEitherNotice() throws java.io.IOException {
        var reservation = create();
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        var event = rabbitEvents().stream().filter(e -> e.getPayload().contains("RESERVATION_CONFIRMED")).findFirst().orElseThrow();
        for (int i=0; i<3; i++) {
            deliverReception(message(event));
            deliverGuest(message(event));
        }
        assertEquals(2, noticeRepository.count());
        assertEquals(2L, jdbc.queryForObject("select count(*) from consumed_events where event_id=?", Long.class, event.getId()));
    }

    @Test void recipientIsTakenFromEventAndListIsRestrictedByTenantAndObjectId() throws java.io.IOException {
        var reservation = create();
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        deliverGuest(message(rabbitEvents().stream()
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

    @Test void receptionCreatedReservationDoesNotNotifyStaffAsIfTheyWereGuest() throws java.io.IOException {
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        var reservation = create();
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        var event = rabbitEvents().stream().filter(e -> e.getPayload().contains("RESERVATION_CONFIRMED")).findFirst().orElseThrow();
        deliverReception(message(event));
        deliverGuest(message(event));
        assertEquals(1, noticeRepository.count());
        assertEquals(1, notices.list(0, 5).getTotalElements());
    }

    @Test void failureAfterSavingNoticeRollsBackNoticeAndInboxReceiptTogether() throws java.io.IOException {
        var event = rabbitEventsAfterCreate();
        assertThrows(IllegalStateException.class, () -> inbox.process("RABBITMQ", event.getPayload(), () -> {
            notices.receive(event.getPayload(), false);
            throw new IllegalStateException("simulated failure");
        }));
        assertEquals(0, noticeRepository.count());
        assertEquals(0L, jdbc.queryForObject("select count(*) from consumed_events", Long.class));
        deliverReception(message(event));
        assertEquals(1, noticeRepository.count());
    }

    @Test void failedStatusEventEnqueueRollsBackStatusAndAudit() throws java.io.IOException {
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

    @Test void oldCreatedEnvelopeWithoutIdentityStillProducesReceptionNotice() throws java.io.IOException {
        String payload = "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"RESERVATION_CREATED\","
            + "\"data\":{\"reservationId\":12,\"unitId\":3,\"status\":\"CREADA\"}}";
        deliverReception(new Message(payload.getBytes(StandardCharsets.UTF_8)));
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        assertEquals(WorkflowIntegrationTests.NOW, notices.list(0, 5).getContent().get(0).occurredAt());
    }

    @Test void eventTimeOrdersNoticesEvenWhenDeliveryOrderIsReversed() throws java.io.IOException {
        var reservation = create();
        authenticate("Recepcionista", WorkflowIntegrationTests.TENANT);
        when(clock.instant()).thenReturn(WorkflowIntegrationTests.NOW.plusSeconds(60));
        reservations.updateStatus(reservation.getId(), "CONFIRMADA");
        when(clock.instant()).thenReturn(WorkflowIntegrationTests.NOW.plusSeconds(120));
        reservations.updateStatus(reservation.getId(), "CANCELADA");
        var events = rabbitEvents();
        deliverGuest(message(events.stream()
            .filter(e -> e.getPayload().contains("RESERVATION_CANCELLED")).findFirst().orElseThrow()));
        deliverGuest(message(events.stream()
            .filter(e -> e.getPayload().contains("RESERVATION_CONFIRMED")).findFirst().orElseThrow()));
        authenticate("Huesped", WorkflowIntegrationTests.TENANT);
        assertEquals(List.of("CANCELADA", "CONFIRMADA"), notices.list(0,5).map(n -> n.status()).getContent());
    }

    @Test void originalPlainTextDeliveryIsAcknowledgedAndDeduplicatedWithoutInventingNotice() throws java.io.IOException {
        var legacy = new Message("Reserva creada: ID=12, Huésped=Ana, Unidad=3".getBytes(StandardCharsets.UTF_8));
        deliverReception(legacy);
        deliverReception(legacy);
        assertEquals(0, noticeRepository.count());
        assertEquals(1L, jdbc.queryForObject("select count(*) from consumed_events", Long.class));
    }

    @Test void ackIsSentAfterCommitAndFailedAckCanBeRedeliveredWithoutAnotherNotice() throws java.io.IOException {
        var event = rabbitEventsAfterCreate();
        Message first = message(event);
        first.getMessageProperties().setDeliveryTag(17);
        doAnswer(invocation -> {
            assertEquals(1, noticeRepository.count(), "El aviso debe estar confirmado antes del ACK");
            assertEquals(1L, jdbc.queryForObject("select count(*) from consumed_events", Long.class));
            throw new java.io.IOException("Canal interrumpido después del commit");
        }).when(channel).basicAck(17, false);

        assertThrows(java.io.IOException.class, () -> receptionConsumer.receive(first, channel));
        verify(channel).abort();
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertEquals(1, noticeRepository.count());

        Channel nextChannel = mock(Channel.class);
        Message redelivery = message(event);
        redelivery.getMessageProperties().setDeliveryTag(23);
        receptionConsumer.receive(redelivery, nextChannel);
        verify(nextChannel).basicAck(23, false);
        verify(nextChannel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertEquals(1, noticeRepository.count());
        assertEquals(1L, jdbc.queryForObject("select count(*) from consumed_events", Long.class));
    }

    static java.util.stream.Stream<String> invalidEvents() {
        return java.util.stream.Stream.of("", "{not-json", "[]",
            "{\"type\":\"RESERVATION_CREATED\",\"data\":{\"reservationId\":0,\"unitId\":1}}",
            "{\"type\":\"RESERVATION_CREATED\",\"occurredAt\":\"not-a-date\","
                + "\"data\":{\"reservationId\":1,\"unitId\":1}}");
    }

    @ParameterizedTest @MethodSource("invalidEvents")
    void invalidEventIsNackedWithoutLeavingNoticeOrInboxReceipt(String payload) throws java.io.IOException {
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8));
        message.getMessageProperties().setDeliveryTag(31);
        deliverReception(message);
        verify(channel).basicNack(31, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        assertEquals(0, noticeRepository.count());
        assertEquals(0L, jdbc.queryForObject("select count(*) from consumed_events", Long.class));
    }

    private void deliverReception(Message message) throws java.io.IOException {
        receptionConsumer.receive(message, channel);
    }

    private void deliverGuest(Message message) throws java.io.IOException {
        guestConsumer.receive(message, channel);
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
