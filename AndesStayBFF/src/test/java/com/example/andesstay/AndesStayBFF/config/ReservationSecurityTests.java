package com.example.andesstay.AndesStayBFF.config;

import com.example.andesstay.AndesStayBFF.controller.BffController;
import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(BffController.class)
@Import(SecurityConfig.class)
class ReservationSecurityTests {

    @Autowired private MockMvc mvc;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private BffReservationService service;

    @BeforeEach
    void stubService() {
        when(service.getReservations(anyString())).thenReturn("[]");
        when(service.createReservation(anyString(), anyString())).thenReturn("{}");
        when(service.updateReservationStatus(anyString(), eq(1L), anyString())).thenReturn("{}");
    }

    @ParameterizedTest
    @CsvSource({
            "Huesped, list, 200", "Huesped, create, 200", "Huesped, status, 403",
            "Admin, list, 200", "Admin, create, 200", "Admin, status, 200",
            "Recepcionista, list, 200", "Recepcionista, create, 200", "Recepcionista, status, 200",
            "Auditor, list, 403", "Auditor, create, 403", "Auditor, status, 403",
            "SinRol, list, 403"
    })
    void bffEnforcesReservationPermissions(String role, String operation, int expectedStatus)
            throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of(role)).build());

        mvc.perform(request(operation).header("Authorization", "Bearer test-token"))
                .andExpect(status().is(expectedStatus));

        if (expectedStatus == 403) verifyNoInteractions(service);
    }

    @Test
    void anonymousRequestRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/bff/reservations")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void unconfiguredReservationMethodIsDenied() throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of("Admin")).build());

        mvc.perform(patch("/api/bff/reservations/1").header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"400, Fechas inválidas", "404, Reserva no encontrada", "409, Fechas ocupadas"})
    void bffPreservesBackendValidationAndConflictResponses(int code, String message) throws Exception {
        authenticateAdmin();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"status\":" + code + ",\"detail\":\"" + message + "\"}";
        when(service.updateReservationStatus(anyString(), eq(1L), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.valueOf(code), message,
                        headers, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        mvc.perform(request("status").header("Authorization", "Bearer test-token"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    void backendInternalErrorIsNotExposed() throws Exception {
        authenticateAdmin();
        when(service.getReservations(anyString())).thenThrow(HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error SQL", new HttpHeaders(),
                "datos-internos".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        mvc.perform(request("list").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("No se pudo completar la solicitud; vuelve a intentarlo"));
    }

    @Test
    void backendConnectionFailureReturnsBadGateway() throws Exception {
        authenticateAdmin();
        when(service.getReservations(anyString())).thenThrow(new ResourceAccessException("Sin conexión"));

        mvc.perform(request("list").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.detail").exists());
    }

    private void authenticateAdmin() {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of("Admin")).build());
    }

    private MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "list" -> get("/api/bff/reservations");
            case "create" -> post("/api/bff/reservations")
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
            case "status" -> put("/api/bff/reservations/1/status")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMADA\"}");
            default -> throw new IllegalArgumentException(operation);
        };
    }
}
