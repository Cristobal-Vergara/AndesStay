package com.example.AndesStay.service;

import com.example.AndesStay.entity.Reservation;
import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.repository.ReservationRepository;
import com.example.AndesStay.repository.UnitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceSecurityTests {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String USER = "22222222-2222-2222-2222-222222222222";
    private static final String OWNER = TENANT + ":" + USER;

    @Mock private ReservationRepository reservations;
    @Mock private UnitRepository units;
    @Mock private ReservationProducer rabbitProducer;
    @Mock private KafkaProducer kafkaProducer;
    @Mock private AuditService audit;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(
                reservations, units, rabbitProducer, kafkaProducer, audit, new ReservationAccess());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void guestListUsesAuthenticatedOwner() {
        authenticate("Huesped", TENANT, USER);
        Reservation ownReservation = reservation();
        when(reservations.findByOwnerId(OWNER)).thenReturn(List.of(ownReservation));

        assertEquals(List.of(ownReservation), service.getReservations());
        verify(reservations, never()).findAll();
    }

    @Test
    void guestLookupDoesNotExposeAnotherOwnersReservation() {
        authenticate("Huesped", TENANT, USER);
        when(reservations.findByIdAndOwnerId(9L, OWNER)).thenReturn(Optional.empty());

        assertTrue(service.getReservationById(9L).isEmpty());
        verify(reservations, never()).findById(anyLong());
    }

    @Test
    void guestCanReadOwnReservation() {
        authenticate("Huesped", TENANT, USER);
        Reservation ownReservation = reservation();
        when(reservations.findByIdAndOwnerId(9L, OWNER)).thenReturn(Optional.of(ownReservation));

        assertSame(ownReservation, service.getReservationById(9L).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Recepcionista"})
    void staffCanReadAllIncludingLegacyReservations(String role) {
        authenticate(role, TENANT, USER);
        Reservation legacyReservation = reservation();
        when(reservations.findAll()).thenReturn(List.of(legacyReservation));
        when(reservations.findById(9L)).thenReturn(Optional.of(legacyReservation));

        assertEquals(List.of(legacyReservation), service.getReservations());
        assertSame(legacyReservation, service.getReservationById(9L).orElseThrow());
        verify(reservations, never()).findByOwnerId(anyString());
    }

    @ParameterizedTest
    @CsvSource({
            "Huesped, edit", "Huesped, delete", "Huesped, status",
            "Auditor, edit", "Auditor, delete", "Auditor, status"
    })
    void guestAndAuditorCannotMutateReservations(String role, String operation) {
        authenticate(role, TENANT, USER);

        assertThrows(AccessDeniedException.class, () -> {
            switch (operation) {
                case "edit" -> service.updateReservation(9L, reservation());
                case "delete" -> service.deleteReservation(9L);
                case "status" -> service.updateStatus(9L, "CONFIRMADA");
                default -> fail("Operación no contemplada por la prueba");
            }
        });
        verifyNoInteractions(reservations, units, rabbitProducer, kafkaProducer, audit);
    }

    @Test
    void creationIgnoresForgedOwnerIdStatusAndReservationId() {
        authenticate("Huesped", TENANT, USER);
        Reservation incoming = reservation();
        incoming.setId(9L);
        incoming.setOwnerId("otro-usuario");
        incoming.setStatus("CONFIRMADA");
        Unit bookableUnit = new Unit();
        bookableUnit.setAvailable(true);
        when(units.findByIdForUpdate(1L)).thenReturn(Optional.of(bookableUnit));
        when(reservations.save(any())).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            assertNull(saved.getId());
            assertEquals(OWNER, saved.getOwnerId());
            assertEquals("CREADA", saved.getStatus());
            saved.setId(10L);
            return saved;
        });

        Reservation result = service.createReservation(incoming);

        assertEquals(10L, result.getId());
        assertEquals(OWNER, result.getOwnerId());
        verify(rabbitProducer).sendReservationCreated(result);
        verify(audit).registerEvent(eq("RESERVA_CREADA"), eq(10L), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"oid", "tid"})
    void missingStableIdentityDeniesReading(String missingClaim) {
        authenticate("Huesped", missingClaim.equals("tid") ? null : TENANT,
                missingClaim.equals("oid") ? null : USER);

        assertThrows(AccessDeniedException.class, () -> service.getReservations());
        verifyNoInteractions(reservations);
    }

    @Test
    void sameObjectIdInDifferentTenantHasDifferentOwner() {
        String otherTenant = "33333333-3333-3333-3333-333333333333";
        authenticate("Huesped", otherTenant, USER);
        when(reservations.findByOwnerId(otherTenant + ":" + USER)).thenReturn(List.of());

        assertTrue(service.getReservations().isEmpty());
        verify(reservations).findByOwnerId(otherTenant + ":" + USER);
        verify(reservations, never()).findByOwnerId(OWNER);
    }

    @Test
    void anonymousCannotReadThroughService() {
        assertThrows(AccessDeniedException.class, () -> service.getReservations());
        verifyNoInteractions(reservations);
    }

    private void authenticate(String role, String tenantId, String objectId) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("subject-is-not-the-owner-key");
        if (tenantId != null) builder.claim("tid", tenantId);
        if (objectId != null) builder.claim("oid", objectId);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                builder.build(), List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private Reservation reservation() {
        return new Reservation(null, "Huésped de prueba", 1L,
                LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12), "CREADA");
    }
}
