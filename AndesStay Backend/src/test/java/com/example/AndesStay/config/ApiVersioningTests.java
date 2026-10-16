package com.example.AndesStay.config;

import com.example.AndesStay.controller.ReservationController;
import com.example.AndesStay.controller.AccountController;
import com.example.AndesStay.repository.UserProfileRepository;
import com.example.AndesStay.service.ReservationService;
import com.example.AndesStay.service.ReservationAccess;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ReservationController.class, AccountController.class})
@Import({SecurityConfig.class, ReservationAccess.class, ApiVersioningTests.TestClock.class})
class ApiVersioningTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UserProfileRepository profiles;
    @MockitoBean ReservationService service;

    @TestConfiguration static class TestClock {
        @Bean Clock clock() { return Clock.fixed(Instant.EPOCH, ZoneId.of("America/Santiago")); }
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/account", "/api/account"})
    void bothAccountRoutesUseTheIdentityFromTheVerifiedJwt(String path) throws Exception {
        when(decoder.decode("version-test")).thenReturn(Jwt.withTokenValue("version-test")
                .header("alg", "RS256").subject("not-the-owner-id").claim("roles", List.of("Huesped"))
                .claim("tid", "test-tenant").claim("oid", "test-user").build());
        mvc.perform(get(path).header("Authorization", "Bearer version-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value("test-tenant"))
                .andExpect(jsonPath("$.objectId").value("test-user"))
                .andExpect(jsonPath("$.timeZone").value("America/Santiago"));
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/reservations", "/api/reservations"})
    void bothRoutesReturnTheSamePaginatedContract(String path) throws Exception {
        authenticate("Huesped");
        when(service.getReservations(0, 20)).thenReturn(Page.empty());
        mvc.perform(get(path).header("Authorization", "Bearer version-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
        verify(service).getReservations(0, 20);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/reservations", "/api/reservations"})
    void bothRoutesRequireAValidJwt(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid test token"));
        mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/reservations", "/api/reservations"})
    void aliasDoesNotGiveAnAuditorGuestPermissions(String path) throws Exception {
        authenticate("Auditor");
        mvc.perform(get(path).header("Authorization", "Bearer version-test")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v2/reservations", "/api/v01/reservations", "/api/v1x/reservations"})
    void unsupportedVersionsHaveNoAccessToReservationService(String path) throws Exception {
        authenticate("Admin");
        mvc.perform(get(path).header("Authorization", "Bearer version-test")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private void authenticate(String role) {
        when(decoder.decode("version-test")).thenReturn(Jwt.withTokenValue("version-test")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of(role)).build());
    }
}
