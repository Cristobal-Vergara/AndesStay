package com.example.AndesStay.service;

import com.example.AndesStay.entity.Reservation;
import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.repository.ReservationRepository;
import com.example.AndesStay.repository.AuditEventRepository;
import com.example.AndesStay.repository.UnitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:availability;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000}",
        "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${TEST_DB_USERNAME:sa}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
        "logging.level.root=WARN", "debug=false"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationService.class, ReservationAccess.class, UnitService.class, AuditService.class, com.example.AndesStay.config.ClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservationAvailabilityIntegrationTests {

    @org.springframework.test.context.DynamicPropertySource
    static void protectExternalDatabase(org.springframework.test.context.DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url != null && !url.matches("jdbc:mysql://localhost:[0-9]+/andesstay_test_[a-z0-9_]+\\?.*")) {
            throw new IllegalStateException("Las pruebas destructivas solo admiten esquemas temporales andesstay_test_ locales");
        }
    }

    @Autowired private ReservationService service;
    @Autowired private UnitService unitService;
    @Autowired private ReservationRepository reservations;
    @Autowired private UnitRepository units;
    @Autowired private AuditEventRepository auditEvents;
    @MockitoBean private ReservationProducer rabbitProducer;
    @MockitoBean private KafkaProducer kafkaProducer;
    @MockitoSpyBean private AuditService audit;

    private Unit unit;

    @BeforeEach
    void setUp() {
        auditEvents.deleteAll();
        reservations.deleteAll();
        units.deleteAll();
        unit = units.saveAndFlush(new Unit(null, "Unidad de prueba", "HABITACION",
                2, new BigDecimal("50000.00"), true, "Solo para pruebas"));
        authenticateStaff();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMADA", "CHECKIN_PENDIENTE", "EN_ESTADÍA"})
    void allOccupiedStatesBlockNewReservations(String state) {
        stored(10, 12, state);

        assertStatus(409, () -> service.createReservation(incoming(11, 13)));
        assertEquals(1, reservations.count());
        verifyNoInteractions(rabbitProducer, kafkaProducer, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREADA", "CANCELADA", "CHECKOUT"})
    void nonOccupiedStatesDoNotBlockNewRequests(String state) {
        stored(10, 12, state);

        Reservation created = service.createReservation(incoming(11, 13));

        assertEquals("CREADA", created.getStatus());
        assertEquals(2, reservations.count());
    }

    @ParameterizedTest
    @CsvSource({"8, 10, true", "12, 14, true", "10, 12, false", "9, 13, false",
            "10, 11, false", "11, 14, false", "9, 11, false"})
    void dateBoundariesUseCheckoutAsAnExclusiveEnd(int start, int end, boolean allowed) {
        stored(10, 12, "CONFIRMADA");
        Reservation candidate = stored(start, end, "CREADA");

        if (allowed) {
            assertEquals("CONFIRMADA", service.updateStatus(candidate.getId(), "CONFIRMADA").getStatus());
        } else {
            assertStatus(409, () -> service.updateStatus(candidate.getId(), "CONFIRMADA"));
            assertEquals("CREADA", reload(candidate).getStatus());
        }
    }

    @ParameterizedTest
    @CsvSource({"10, 10", "12, 10"})
    void invalidDatesAreRejectedOnCreationAndEditing(int start, int end) {
        Reservation candidate = stored(14, 16, "CONFIRMADA");

        assertStatus(400, () -> service.createReservation(incoming(start, end)));
        assertStatus(400, () -> service.updateReservation(candidate.getId(), incoming(start, end)));

        assertEquals(date(14), reload(candidate).getCheckIn());
        assertEquals(date(16), reload(candidate).getCheckOut());
        verifyNoInteractions(rabbitProducer, kafkaProducer, audit);
    }

    @Test
    void missingRequiredFieldsAreRejectedWithoutSaving() {
        Reservation missingDate = incoming(10, 12);
        missingDate.setCheckIn(null);
        assertStatus(400, () -> service.createReservation(missingDate));
        missingDate.setCheckIn(date(10));
        missingDate.setCheckOut(null);
        assertStatus(400, () -> service.createReservation(missingDate));
        Reservation blankGuest = incoming(10, 12);
        blankGuest.setGuestName("  ");
        assertStatus(400, () -> service.createReservation(blankGuest));
        Reservation invalidUnit = incoming(10, 12);
        invalidUnit.setUnitId(0L);
        assertStatus(400, () -> service.createReservation(invalidUnit));
        invalidUnit.setUnitId(null);
        assertStatus(400, () -> service.createReservation(invalidUnit));
        invalidUnit.setUnitId(Long.MAX_VALUE);
        assertStatus(404, () -> service.createReservation(invalidUnit));
        assertEquals(0, reservations.count());
    }

    @Test
    void disabledUnitRejectsCreationAndConfirmation() {
        Reservation candidate = stored(10, 12, "CREADA");
        unit.setAvailable(false);
        units.saveAndFlush(unit);

        assertStatus(409, () -> service.createReservation(incoming(10, 12)));
        assertStatus(409, () -> service.updateStatus(candidate.getId(), "CONFIRMADA"));
        assertEquals("CREADA", reload(candidate).getStatus());
    }

    @Test
    void disabledUnitStillAllowsCancellationAndCheckout() {
        Reservation confirmed = stored(10, 12, "CONFIRMADA");
        Reservation staying = stored(14, 16, "EN_ESTADÍA");
        unit.setAvailable(false);
        units.saveAndFlush(unit);

        assertEquals("CANCELADA", service.updateStatus(confirmed.getId(), "CANCELADA").getStatus());
        assertEquals("CHECKOUT", service.updateStatus(staying.getId(), "CHECKOUT").getStatus());
    }

    @Test
    void editingCannotIntroduceOverlapOrOverwriteOwnerAndState() {
        stored(10, 12, "EN_ESTADÍA");
        Reservation candidate = stored(14, 16, "CONFIRMADA");
        candidate.setOwnerId("original-owner");
        reservations.saveAndFlush(candidate);

        assertStatus(409, () -> service.updateReservation(candidate.getId(), incoming(11, 13)));
        assertEquals(date(14), reload(candidate).getCheckIn());

        Reservation valid = incoming(14, 16);
        valid.setOwnerId("forged-owner");
        valid.setStatus("CHECKOUT");
        service.updateReservation(candidate.getId(), valid);

        assertEquals("original-owner", reload(candidate).getOwnerId());
        assertEquals("CONFIRMADA", reload(candidate).getStatus());
    }

    @Test
    void movingToOccupiedOrDisabledUnitIsRejected() {
        Reservation occupied = stored(10, 12, "CONFIRMADA");
        Unit second = units.saveAndFlush(new Unit(null, "Segunda unidad", "CABANA",
                4, new BigDecimal("80000.00"), true, "Solo para pruebas"));
        Reservation candidate = new Reservation(null, "Prueba", second.getId(),
                date(10), date(12), "CONFIRMADA");
        candidate = reservations.saveAndFlush(candidate);
        Long candidateId = candidate.getId();

        assertStatus(409, () -> service.updateReservation(candidateId, incoming(10, 12)));
        assertEquals(second.getId(), reload(candidate).getUnitId());
        unit.setAvailable(false);
        units.saveAndFlush(unit);
        assertStatus(409, () -> service.updateReservation(candidateId, incoming(14, 16)));
        assertEquals(second.getId(), reload(candidate).getUnitId());
        assertEquals("CONFIRMADA", reload(occupied).getStatus());
    }

    @Test
    void reservationDoesNotOverlapWithItselfDuringItsLifecycle() {
        Reservation candidate = stored(10, 12, "CREADA");

        service.updateStatus(candidate.getId(), "CONFIRMADA");
        service.updateStatus(candidate.getId(), "CHECKIN_PENDIENTE");
        service.updateStatus(candidate.getId(), "EN_ESTADÍA");
        service.updateStatus(candidate.getId(), "CHECKOUT");

        assertEquals("CHECKOUT", reload(candidate).getStatus());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"DESCONOCIDO", "CHECKOUT"})
    void missingOrInvalidTransitionsReturnBadRequest(String newState) {
        Reservation candidate = stored(10, 12, "CREADA");

        assertStatus(400, () -> service.updateStatus(candidate.getId(), newState));
        assertEquals("CREADA", reload(candidate).getStatus());
        verifyNoInteractions(audit);
    }

    @Test
    void auditFailureRollsBackTheStatusChange() {
        Reservation candidate = stored(10, 12, "CREADA");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Fallo simulado después de guardar la auditoría");
        }).when(audit)
                .registerEvent(eq("RESERVA_CONFIRMADA"), eq(candidate.getId()), anyString());

        assertThrows(IllegalStateException.class, () -> service.updateStatus(candidate.getId(), "CONFIRMADA"));

        assertEquals("CREADA", reload(candidate).getStatus());
        assertEquals(0, auditEvents.count());
    }

    @Test
    void unitWithAnyReservationCannotBeDeleted() {
        stored(10, 12, "CANCELADA");

        assertStatus(409, () -> unitService.deleteUnit(unit.getId()));
        assertTrue(units.existsById(unit.getId()));
    }

    @Test
    void unitWithoutReservationsCanBeDeleted() {
        unitService.deleteUnit(unit.getId());
        assertFalse(units.existsById(unit.getId()));
    }

    @Test
    void unitCreationCannotOverwriteAnExistingUnitsAvailability() {
        Unit incoming = new Unit(unit.getId(), "Nueva unidad", "HABITACION",
                2, new BigDecimal("50000.00"), false, "Solo para pruebas");

        Unit created = unitService.createUnit(incoming);

        assertNotEquals(unit.getId(), created.getId());
        assertTrue(units.findById(unit.getId()).orElseThrow().getAvailable());
        assertFalse(created.getAvailable());
    }

    @Test
    void synchronousBrokerFailureRollsBackNewReservation() {
        doThrow(new IllegalStateException("Fallo simulado de RabbitMQ"))
                .when(rabbitProducer).sendReservationCreated(any());

        assertThrows(IllegalStateException.class, () -> service.createReservation(incoming(10, 12)));

        assertEquals(0, reservations.count());
        assertEquals(0, auditEvents.count());
    }

    @Test
    void simultaneousConfirmationsCannotDoubleBookTheUnit() throws Exception {
        Reservation first = stored(10, 12, "CREADA");
        Reservation second = stored(11, 13, "CREADA");
        CountDownLatch firstBeforeCommit = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            firstBeforeCommit.countDown();
            if (!allowFirstCommit.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tiempo de espera agotado en la prueba");
            }
            return result;
        }).when(audit).registerEvent(eq("RESERVA_CONFIRMADA"), eq(first.getId()), anyString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(() -> confirmAsStaff(first.getId()));
            assertTrue(firstBeforeCommit.await(5, TimeUnit.SECONDS));
            Future<Integer> secondResult = executor.submit(() -> {
                secondStarted.countDown();
                return confirmAsStaff(second.getId());
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            // La primera transacción sigue abierta; la segunda debe esperar al bloqueo.
            assertThrows(TimeoutException.class, () -> secondResult.get(500, TimeUnit.MILLISECONDS));
            allowFirstCommit.countDown();

            assertEquals(200, firstResult.get(5, TimeUnit.SECONDS));
            assertEquals(409, secondResult.get(5, TimeUnit.SECONDS));
            assertEquals(1, reservations.countByStatus("CONFIRMADA"));
            assertEquals("CREADA", reload(second).getStatus());
            assertEquals(1, auditEvents.count());
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private int confirmAsStaff(Long id) {
        authenticateStaff();
        try {
            service.updateStatus(id, "CONFIRMADA");
            return 200;
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateStaff() {
        Jwt token = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("staff")
                .claim("tid", "11111111-1111-1111-1111-111111111111")
                .claim("oid", "22222222-2222-2222-2222-222222222222").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                token, List.of(new SimpleGrantedAuthority("ROLE_Recepcionista"))));
    }

    private Reservation stored(int start, int end, String state) {
        Reservation reservation = incoming(start, end);
        reservation.setStatus(state);
        return reservations.saveAndFlush(reservation);
    }

    private Reservation incoming(int start, int end) {
        return new Reservation(null, "Huésped de prueba", unit.getId(), date(start), date(end), "CREADA");
    }

    private LocalDate date(int day) {
        return LocalDate.of(2030, 1, day);
    }

    private Reservation reload(Reservation reservation) {
        return reservations.findById(reservation.getId()).orElseThrow();
    }

    private void assertStatus(int expected, Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, exception.getStatusCode().value());
    }
}
