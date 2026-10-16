package com.example.AndesStay.config;

import com.example.AndesStay.controller.ReservationController;
import com.example.AndesStay.entity.Reservation;
import com.example.AndesStay.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationSecurityTests {
    @MockitoBean com.example.AndesStay.repository.UserProfileRepository profiles;

    @Autowired private MockMvc mvc;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private ReservationService service;

    @BeforeEach
    void stubService() {
        when(service.getReservations(anyInt(), anyInt())).thenReturn(org.springframework.data.domain.Page.empty());
        when(service.getReservationById(1L)).thenReturn(Optional.of(new Reservation()));
        when(service.createReservation(any())).thenReturn(new Reservation());
        when(service.updateReservation(eq(1L), any())).thenReturn(new Reservation());
        when(service.updateStatus(eq(1L), any())).thenReturn(new Reservation());
        when(service.deleteReservation(1L)).thenReturn(true);
    }

    @ParameterizedTest
    @CsvSource({
            "Huesped, list, 200", "Huesped, detail, 200", "Huesped, create, 200",
            "Huesped, edit, 403", "Huesped, status, 403", "Huesped, delete, 403",
            "Admin, list, 200", "Admin, create, 200", "Admin, edit, 200",
            "Admin, status, 200", "Admin, delete, 204",
            "Recepcionista, list, 200", "Recepcionista, create, 200",
            "Recepcionista, edit, 200", "Recepcionista, status, 200", "Recepcionista, delete, 204",
            "Auditor, list, 403", "Auditor, create, 403", "Auditor, status, 403",
            "SinRol, list, 403"
    })
    void permissionsAreEnforcedByHttpMethod(String role, String operation, int expectedStatus)
            throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of(role)).build());

        mvc.perform(request(operation).header("Authorization", "Bearer test-token"))
                .andExpect(status().is(expectedStatus));

        if (expectedStatus == 403) verifyNoInteractions(service);
    }

    @Test
    void anonymousRequestRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/reservations")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void unconfiguredReservationMethodIsDenied() throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of("Admin")).build());

        mvc.perform(patch("/api/v1/reservations/1").header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"400, Fechas inválidas", "404, Reserva no encontrada", "409, Fechas ocupadas"})
    void reservationErrorsHaveTheirOriginalStatusAndMessage(int code, String message) throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of("Admin")).build());
        when(service.updateStatus(eq(1L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.valueOf(code), message));

        mvc.perform(request("status").header("Authorization", "Bearer test-token"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    void lockFailureReturnsConflictWithoutDatabaseDetails() throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of("Admin")).build());
        when(service.updateStatus(eq(1L), any()))
                .thenThrow(new CannotAcquireLockException("SQL interno de prueba"));

        mvc.perform(request("status").header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Otra operación está modificando la reserva o la unidad; vuelve a intentarlo"));
    }

    @Test
    void malformedDateReturnsBadRequest() throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of("Huesped")).build());

        mvc.perform(post("/api/v1/reservations").header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkIn\":\"fecha-inválida\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "list" -> get("/api/v1/reservations");
            case "detail" -> get("/api/v1/reservations/1");
            case "create" -> post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
            case "edit" -> put("/api/v1/reservations/1")
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
            case "status" -> put("/api/v1/reservations/1/status")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMADA\"}");
            case "delete" -> delete("/api/v1/reservations/1");
            default -> throw new IllegalArgumentException(operation);
        };
    }
}
