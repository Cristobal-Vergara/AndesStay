package com.example.AndesStay.service;

import com.example.AndesStay.entity.*;
import com.example.AndesStay.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties={
    "spring.datasource.url=jdbc:h2:mem:workflow;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
    "spring.jpa.show-sql=false"
}, showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationService.class, ReservationAccess.class, UnitService.class, AuditService.class,
    KafkaProducer.class, ReservationProducer.class, OutboxService.class, OutboxDispatchService.class,
    InboxService.class, ReportService.class, UserProfileService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class WorkflowIntegrationTests {
    static final String TENANT="11111111-1111-1111-1111-111111111111";
    static final String STAFF="22222222-2222-2222-2222-222222222222";
    static final String GUEST="33333333-3333-3333-3333-333333333333";
    static final Instant NOW=Instant.parse("2026-10-04T02:30:00Z");
    @Autowired ReservationService service;
    @Autowired UnitService catalog;
    @Autowired ReportService reports;
    @Autowired ReservationRepository reservations;
    @Autowired UnitRepository units;
    @Autowired AuditEventRepository audits;
    @Autowired OutboxRepository outbox;
    @Autowired OutboxDispatchService dispatch;
    @Autowired InboxService inbox;
    @Autowired UserProfileService registration;
    @Autowired UserProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    @MockitoBean BrokerGateway gateway;
    @MockitoSpyBean OutboxService enqueue;
    Unit unit;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Santiago"));
        jdbc.update("delete from consumed_events");
        outbox.deleteAll(); audits.deleteAll(); reservations.deleteAll(); units.deleteAll(); profiles.deleteAll();
        unit=units.saveAndFlush(new Unit(null,"Habitación","HABITACION",2,new BigDecimal("50000"),true,"Prueba"));
        authenticate("Recepcionista",TENANT,STAFF);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void creationCommitsReservationAuditAndThreePendingEventsWithoutContactingBrokers() {
        var reservation=service.createReservation(incoming());
        assertFalse(reservation.isGuestAssigned());
        assertEquals(1,reservations.count()); assertEquals(1,audits.count()); assertEquals(3,outbox.count());
        assertTrue(outbox.findAll().stream().allMatch(e->e.getPublishedAt()==null));
        verifyNoInteractions(gateway);
    }
    @Test void publicRegistrationImmediatelyAllowsOnlyOwnReservationsWithTheSameJwt() {
        var unrelated=service.createReservation(incoming());
        var jwt=Jwt.withTokenValue("public").header("alg","RS256").subject(GUEST)
            .claim("tid",TENANT).claim("oid",GUEST).claim("scp","access_as_user").build();
        var converter=new com.example.AndesStay.config.SecurityConfig().jwtAuthenticationConverter(profiles);
        SecurityContextHolder.getContext().setAuthentication(converter.convert(jwt));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.createReservation(incoming()));
        assertEquals(List.of("Huesped"),registration.register("Ana Torres",jwt).roles());
        SecurityContextHolder.getContext().setAuthentication(converter.convert(jwt));
        var forged=incoming(); forged.setOwnerId(TENANT+":"+STAFF);
        var own=service.createReservation(forged);
        assertEquals(TENANT+":"+GUEST,own.getOwnerId()); assertTrue(own.isGuestAssigned());
        assertEquals(1,service.getReservations(0,20).getTotalElements());
        assertEquals(own.getId(),service.getReservations(0,20).getContent().get(0).getId());
        assertTrue(service.getReservationById(unrelated.getId()).isEmpty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.updateStatus(own.getId(),"CONFIRMADA"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->catalog.deleteUnit(unit.getId()));
    }
    @Test void failureToStoreAnEventRollsBackReservationAuditAndAllOtherEvents() {
        OutboxService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(enqueue);
        doThrow(new IllegalStateException("db unavailable")).when(target).enqueue(eq("KAFKA"),eq("AUDIT_EVENT"),any());
        assertThrows(IllegalStateException.class,()->service.createReservation(incoming()));
        assertEquals(0,reservations.count()); assertEquals(0,audits.count()); assertEquals(0,outbox.count());
    }
    @Test void brokerFailureKeepsCommittedReservationAndRetriesSameEventAfterDelay() throws Exception {
        service.createReservation(incoming());
        var event=outbox.findAll().get(0);
        doThrow(new IllegalStateException("offline")).when(gateway).publish(any());
        dispatch.dispatch(event.getId());
        var failed=outbox.findById(event.getId()).orElseThrow();
        assertNull(failed.getPublishedAt()); assertEquals(1,failed.getAttempts());
        assertEquals(1,reservations.count());
        dispatch.dispatch(event.getId());
        verify(gateway,times(1)).publish(any());
        when(clock.instant()).thenReturn(NOW.plusSeconds(3));
        doNothing().when(gateway).publish(any());
        dispatch.dispatch(event.getId()); dispatch.dispatch(event.getId());
        var published=outbox.findById(event.getId()).orElseThrow();
        assertNotNull(published.getPublishedAt()); assertEquals(2,published.getAttempts());
        verify(gateway,times(2)).publish(argThat(e->e.getId().equals(event.getId())));
    }
    @Test void duplicateDeliveryIsProcessedOncePerDestination() {
        String payload="{\"id\":\""+UUID.randomUUID()+"\"}";
        AtomicInteger calls=new AtomicInteger();
        assertTrue(inbox.process("KAFKA",payload,calls::incrementAndGet));
        assertFalse(inbox.process("KAFKA",payload,calls::incrementAndGet));
        assertTrue(inbox.process("RABBITMQ",payload,calls::incrementAndGet));
        assertEquals(2,calls.get());
    }
    @Test void consumerFailureRollsBackDeduplicationSoRedeliveryCanSucceed() {
        String payload="{\"id\":\""+UUID.randomUUID()+"\"}";
        assertThrows(IllegalStateException.class,()->inbox.process("KAFKA",payload,()->{throw new IllegalStateException();}));
        assertTrue(inbox.process("KAFKA",payload,()->{}));
    }
    @Test void maximumLengthGuestNameCanBeAudited() {
        var input=incoming(); input.setGuestName("A".repeat(255));
        service.createReservation(input);
        assertTrue(audits.findAll().get(0).getDescription().length()>255);
    }
    @Test void guestCreationIgnoresForgedIdentityAndCanOnlyPageOwnReservations() {
        var staffReservation=service.createReservation(incoming());
        authenticate("Huesped",TENANT,GUEST);
        var input=incoming(); input.setOwnerId(TENANT+":"+STAFF); input.setGuestAssigned(false);
        var own=service.createReservation(input);
        assertTrue(own.isGuestAssigned()); assertEquals(TENANT+":"+GUEST,own.getOwnerId());
        assertEquals(1,service.getReservations(0,1).getTotalElements());
        assertEquals(own.getId(),service.getReservations(0,1).getContent().get(0).getId());
        assertTrue(service.getReservationById(staffReservation.getId()).isEmpty());
    }
    @Test void assignmentUsesStaffTenantAndChangesWhichGuestCanRead() {
        var reservation=service.createReservation(incoming());
        assertTrue(service.assignOwner(reservation.getId(),GUEST).isGuestAssigned());
        authenticate("Huesped",TENANT,GUEST);
        assertTrue(service.getReservationById(reservation.getId()).isPresent());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.assignOwner(reservation.getId(),STAFF));
        authenticate("Huesped","44444444-4444-4444-4444-444444444444",GUEST);
        assertTrue(service.getReservationById(reservation.getId()).isEmpty());
    }
    @Test void malformedAccountAndUnboundedPaginationAreRejected() {
        var reservation=service.createReservation(incoming());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.assignOwner(reservation.getId(),"email@example.org")).getStatusCode().value());
        for(int[] input:List.of(new int[]{-1,20},new int[]{0,0},new int[]{0,101}))
            assertEquals(400,assertThrows(ResponseStatusException.class,()->service.getReservations(input[0],input[1])).getStatusCode().value());
    }
    @Test void invalidCatalogValuesDoNotOverwriteStoredUnit() {
        for(String price:List.of("0","-1","1.001")) {
            var input=new Unit(null,"Otra","HABITACION",2,new BigDecimal(price),true,"");
            assertThrows(ResponseStatusException.class,()->catalog.updateUnit(unit.getId(),input));
        }
        var input=new Unit(null,"Otra","HABITACION",0,BigDecimal.ONE,true,"");
        assertThrows(ResponseStatusException.class,()->catalog.createUnit(input));
        assertEquals("Habitación",units.findById(unit.getId()).orElseThrow().getName());
    }
    @Test void referencedUnitCannotBeDeletedAndGuestCannotEditCatalog() {
        service.createReservation(incoming());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->catalog.deleteUnit(unit.getId())).getStatusCode().value());
        authenticate("Huesped",TENANT,GUEST);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->catalog.deleteUnit(unit.getId()));
    }
    @Test void occupancyCountsDistinctExistingUnitsAndCycleCountsFirstValidConfirmation() {
        units.saveAndFlush(new Unit(null,"Libre","HABITACION",1,BigDecimal.ONE,true,""));
        for(int i=0;i<2;i++) {var r=incoming();r.setStatus("EN_ESTADÍA");reservations.saveAndFlush(r);}
        var orphan=incoming();orphan.setUnitId(9999L);orphan.setStatus("EN_ESTADÍA");reservations.saveAndFlush(orphan);
        LocalDateTime start=LocalDateTime.of(2026,10,3,9,0);
        audit(777,"RESERVA_CONFIRMADA",start.minusMinutes(5));
        audit(777,"RESERVA_CREADA",start);
        audit(777,"RESERVA_CREADA",start.plusMinutes(1));
        audit(777,"RESERVA_CONFIRMADA",start.plusMinutes(10));
        audit(777,"RESERVA_CONFIRMADA",start.plusMinutes(20));
        audit(778,"RESERVA_CREADA",start.plusHours(1));
        var metrics=reports.getReservationKPIs();
        assertEquals(1L,metrics.get("ocupacionActiva")); assertEquals(50.0,metrics.get("ocupacionPorcentaje"));
        assertEquals(1,metrics.get("reservasConCiclo"));assertEquals(10.0,metrics.get("tiempoCicloPromedioMinutos"));
        assertEquals(24,((Map<?,?>)metrics.get("reservasPorHora")).size());
        assertEquals("America/Santiago",metrics.get("timeZone"));
    }
    @Test void operationsUseChileDateAtUtcDayBoundaryAndExcludeCancelledReservations() {
        LocalDate today=LocalDate.of(2026,10,3);
        var arrival=incoming();arrival.setCheckIn(today);arrival.setStatus("CONFIRMADA");reservations.saveAndFlush(arrival);
        var cancelled=incoming();cancelled.setCheckIn(today);cancelled.setStatus("CANCELADA");reservations.saveAndFlush(cancelled);
        var departure=incoming();departure.setCheckOut(today);departure.setStatus("EN_ESTADÍA");reservations.saveAndFlush(departure);
        var operations=reports.getDailyOperations(null);
        assertEquals("2026-10-03",operations.get("fecha"));
        assertEquals(1,((List<?>)operations.get("llegadas")).size());assertEquals(1,((List<?>)operations.get("salidas")).size());
    }
    private void audit(long id,String action,LocalDateTime time) {
        audits.saveAndFlush(new AuditEvent(null,action,id,"Prueba",time,"Personal"));
    }
    private Reservation incoming() {
        return new Reservation(null,"Ana",unit.getId(),LocalDate.of(2026,10,5),LocalDate.of(2026,10,7),"CREADA");
    }
    static void authenticate(String role,String tenant,String oid) {
        var jwt=Jwt.withTokenValue("test").header("alg","none").subject(oid).claim("tid",tenant).claim("oid",oid).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
}
