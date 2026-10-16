package com.example.AndesStay.config;

import com.example.AndesStay.controller.ListenerControlController;
import com.example.AndesStay.dto.ListenerStatusResponse;
import com.example.AndesStay.dto.ListenerStatusResponse.State;
import com.example.AndesStay.service.ListenerControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ListenerControlController.class)
@Import(SecurityConfig.class)
class ListenerControlSecurityTests {
    private static final String BASE = "/api/v1/messaging/listeners";
    private static final String ID = "receptionNotifications";
    @MockitoBean com.example.AndesStay.repository.UserProfileRepository profiles;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean ListenerControlService service;
    @Autowired MockMvc mvc;

    @ParameterizedTest @CsvSource({"Admin,200", "Recepcionista,200", "Huesped,403", "Auditor,403"})
    void onlyStaffCanReadState(String role, int expected) throws Exception {
        authenticate(role);
        when(service.list()).thenReturn(List.of(result(State.RUNNING)));
        when(service.status(ID)).thenReturn(result(State.RUNNING));
        mvc.perform(get(BASE).header("Authorization", "Bearer test")).andExpect(status().is(expected));
        mvc.perform(get(BASE + "/" + ID).header("Authorization", "Bearer test")).andExpect(status().is(expected));
        if (expected == 403) verifyNoInteractions(service);
    }

    @ParameterizedTest @CsvSource({"Admin,pause,200", "Admin,resume,200", "Recepcionista,pause,403",
            "Recepcionista,resume,403", "Huesped,pause,403", "Huesped,resume,403", "Auditor,pause,403", "Auditor,resume,403"})
    void onlyAdminCanChangeState(String role, String action, int expected) throws Exception {
        authenticate(role);
        when(service.pause(ID)).thenReturn(result(State.STOPPED));
        when(service.resume(ID)).thenReturn(result(State.RUNNING));
        mvc.perform(post(BASE + "/" + ID + "/" + action).header("Authorization", "Bearer test"))
                .andExpect(status().is(expected));
        if (expected == 403) verifyNoInteractions(service);
    }

    @Test void pendingShutdownReturnsAcceptedAndConflictHasSafeDetail() throws Exception {
        authenticate("Admin");
        when(service.pause(ID)).thenReturn(result(State.STOPPING));
        when(service.resume(ID)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "La pausa aún no termina."));
        mvc.perform(post(BASE + "/" + ID + "/pause").header("Authorization", "Bearer test"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("STOPPING"));
        mvc.perform(post(BASE + "/" + ID + "/resume").header("Authorization", "Bearer test"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("La pausa aún no termina."));
    }

    @Test void anonymousInvalidTokensAndOtherMethodsAreDenied() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/" + ID + "/pause")).andExpect(status().isUnauthorized());
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid JWT"));
        mvc.perform(post(BASE + "/" + ID + "/resume").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        authenticate("Admin");
        mvc.perform(delete(BASE + "/" + ID).header("Authorization", "Bearer test")).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + ID + "/delete").header("Authorization", "Bearer test")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void missingListenerReturnsNotFound() throws Exception {
        authenticate("Admin");
        when(service.pause("unknown")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor no disponible."));
        mvc.perform(post(BASE + "/unknown/pause").header("Authorization", "Bearer test"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Consumidor no disponible."));
    }

    private ListenerStatusResponse result(State state) {
        return new ListenerStatusResponse(ID, "Recepción", List.of("temporary.reception"), state);
    }
    private void authenticate(String role) {
        when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg", "RS256")
                .subject("test-user").claim("roles", List.of(role)).build());
    }
}
