package com.example.AndesStay.service;

import com.example.AndesStay.repository.UserProfileRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DataJpaTest(properties="spring.datasource.url=jdbc:h2:mem:profiles;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({UserProfileService.class, ReservationAccess.class})
class UserProfileIntegrationTests {
    @Autowired UserProfileService service;
    @Autowired UserProfileRepository profiles;
    @Autowired ReservationAccess access;
    @MockitoBean Clock clock;
    static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    Jwt jwt;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        jwt = authenticate("tenant","object","verified@example.test");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void persistsProfileAndGrantsOnlyGuestAccessOnNextAuthenticatedRequest() {
        var result=service.register("  Ana Torres  ",jwt);
        assertTrue(result.registered()); assertEquals("Ana Torres",result.displayName()); assertEquals(NOW,result.createdAt());
        assertEquals("verified@example.test",result.email());
        var stored=profiles.findByOwnerKey("tenant:object").orElseThrow();
        assertEquals("Ana Torres",stored.getDisplayName());
        assertEquals(result,service.get(jwt));
        assertEquals(List.of("Huesped"),result.roles());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().isEmpty());
        assertThrows(AccessDeniedException.class,access::currentOwnerId);
        var auth=new com.example.AndesStay.config.SecurityConfig().jwtAuthenticationConverter(profiles).convert(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertEquals("tenant:object",access.currentOwnerId());
        assertThrows(AccessDeniedException.class,access::requireStaff);
    }
    @Test void sameIdentityCanHaveOnlyOneProfileAndCannotOverwriteIt() {
        service.register("Ana Torres",jwt);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.register("Otro nombre",jwt)).getStatusCode().value());
        assertEquals(1,profiles.count()); assertEquals("Ana Torres",service.get(jwt).displayName());
    }
    @Test void anotherIdentityCannotSeeAnExistingProfileEvenWithSameEmail() {
        service.register("Ana Torres",jwt);
        jwt=authenticate("tenant","another-object","verified@example.test");
        assertFalse(service.get(jwt).registered());
        service.register("Otra persona",jwt);
        assertEquals(2,profiles.count()); assertEquals("Otra persona",service.get(jwt).displayName());
    }
    @Test void sameObjectInDifferentTenantHasSeparateProfile() {
        service.register("Ana Torres",jwt);
        jwt=authenticate("another-tenant","object","verified@example.test");
        assertFalse(service.get(jwt).registered());
    }
    @Test void invalidNamesNeverCreateAProfile() {
        for(String name : new String[]{null," ","A","A".repeat(121),"Ana\nTorres"}) {
            assertEquals(400,assertThrows(ResponseStatusException.class,()->service.register(name,jwt)).getStatusCode().value());
        }
        assertEquals(0,profiles.count());
    }
    @Test void emailIsOptionalAndComesOnlyFromSignedClaims() {
        jwt=authenticate("tenant","object",null);
        assertEquals("",service.register("Ana Torres",jwt).email());
    }
    @Test void emailFallbackUsesSignedEmailClaim() {
        jwt=Jwt.withTokenValue("test").header("alg","RS256").subject("object")
            .claim("tid","tenant").claim("oid","object").claim("scp","access_as_user").claim("email","fallback@example.test").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of()));
        assertEquals("fallback@example.test",service.register("Ana Torres",jwt).email());
    }
    static Jwt authenticate(String tenant,String oid,String email) {
        var builder=Jwt.withTokenValue("test").header("alg","RS256").subject(oid).claim("tid",tenant).claim("oid",oid).claim("scp","access_as_user");
        if(email!=null) builder.claim("preferred_username",email);
        var jwt=builder.build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of()));
        return jwt;
    }
}
