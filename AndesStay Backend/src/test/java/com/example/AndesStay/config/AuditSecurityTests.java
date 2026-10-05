package com.example.AndesStay.config;

import com.example.AndesStay.controller.AuditController;
import com.example.AndesStay.repository.AuditEventRepository;
import com.example.AndesStay.service.AuditService;
import com.example.AndesStay.service.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, AuditService.class, ClockConfig.class})
class AuditSecurityTests {
    @MockitoBean com.example.AndesStay.repository.UserProfileRepository profiles;

    @Autowired private MockMvc mvc;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private AuditEventRepository repository;
    @MockitoBean private KafkaProducer kafka;

    @org.junit.jupiter.api.BeforeEach
    void stubPage() {
        when(repository.findFilteredEventsPage(any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
    }

    @ParameterizedTest
    @CsvSource({"Admin,200", "Auditor,200", "Recepcionista,403", "Huesped,403", "SinRol,403"})
    void onlyAdminAndAuditorCanReadEvents(String role, int code) throws Exception {
        authenticate(role);
        mvc.perform(get("/api/audit").header("Authorization", "Bearer test-token"))
                .andExpect(status().is(code));
        if (code == 403) verifyNoInteractions(repository, kafka);
    }

    @Test
    void anonymousRequestRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/audit")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, kafka);
    }

    @Test
    void httpFiltersReachTheRepositoryTogether() throws Exception {
        authenticate("Auditor");
        mvc.perform(get("/api/audit").header("Authorization", "Bearer test-token")
                .param("user", " alice ").param("action", "RESERVA_CREADA")
                .param("inicio", "2026-10-02T10:00").param("fin", "2026-10-03T10:00"))
                .andExpect(status().isOk());

        verify(repository).findFilteredEventsPage(eq("alice"), eq("RESERVA_CREADA"),
                eq(LocalDateTime.of(2026, 10, 2, 10, 0)), eq(LocalDateTime.of(2026, 10, 3, 10, 0)), any());
    }

    @ParameterizedTest
    @CsvSource({"inicio,fecha-inválida", "fin,2026-02-30T10:00", "inicio,2026-10-02"})
    void malformedDatesReturnBadRequestWithAnExplanation(String field, String value) throws Exception {
        authenticate("Admin");
        mvc.perform(get("/api/audit").header("Authorization", "Bearer test-token").param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("El filtro " + field)));
        verifyNoInteractions(repository, kafka);
    }

    @Test
    void reversedRangeReturnsBadRequestWithoutQuerying() throws Exception {
        authenticate("Admin");
        mvc.perform(get("/api/audit").header("Authorization", "Bearer test-token")
                .param("inicio", "2026-10-03T10:00").param("fin", "2026-10-02T10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha de inicio no puede ser posterior a la fecha de fin"));
        verifyNoInteractions(repository, kafka);
    }

    private void authenticate(String role) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of(role)).build());
    }
}
