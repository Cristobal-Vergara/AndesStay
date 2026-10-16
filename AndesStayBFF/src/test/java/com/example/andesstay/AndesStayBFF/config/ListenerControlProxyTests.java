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
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = BffController.class, properties = "backend.url=http://backend.test")
@Import({SecurityConfig.class, BffReservationService.class, AuditProxyTests.ClientConfiguration.class})
class ListenerControlProxyTests {
    private static final String BASE = "/api/v1/bff/messaging/listeners";
    @Autowired MockMvc mvc;
    @Autowired AuditProxyTests.ClientFixture fixture;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup() { fixture.server().reset(); }

    @Test void staffCanReadAllListenersAndIndividualStateWithVerifiedToken() throws Exception {
        authenticate("Recepcionista");
        for (String suffix : List.of("", "/guestNotifications")) {
            fixture.server().expect(requestTo("http://backend.test/api/v1/messaging/listeners" + suffix))
                    .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer test-token"))
                    .andRespond(withSuccess(suffix.isEmpty() ? "[]" : "{\"state\":\"RUNNING\"}", MediaType.APPLICATION_JSON));
        }
        mvc.perform(get(BASE).header("Authorization", "Bearer test-token")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/guestNotifications").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("RUNNING"));
        fixture.server().verify();
    }

    @ParameterizedTest @CsvSource({"pause,202,STOPPING", "pause,200,STOPPED", "resume,200,RUNNING"})
    void adminChangesStateWithoutPayloadAndPreservesBackendStatus(String action, int expected, String state) throws Exception {
        authenticate("Admin");
        fixture.server().expect(requestTo("http://backend.test/api/v1/messaging/listeners/receptionNotifications/" + action))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(""))
                .andRespond(withStatus(HttpStatus.valueOf(expected)).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"state\":\"" + state + "\"}"));
        mvc.perform(post(BASE + "/receptionNotifications/" + action).header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"queue\":\"forged\"}"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.state").value(state));
        fixture.server().verify();
    }

    @ParameterizedTest @CsvSource({"404", "409"})
    void backendClientErrorsReachTheUser(int expected) throws Exception {
        authenticate("Admin");
        fixture.server().expect(requestTo("http://backend.test/api/v1/messaging/listeners/receptionNotifications/resume"))
                .andRespond(withStatus(HttpStatus.valueOf(expected)).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"detail\":\"Consumidor no disponible.\"}"));
        mvc.perform(post(BASE + "/receptionNotifications/resume").header("Authorization", "Bearer test-token"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.detail").value("Consumidor no disponible."));
        fixture.server().verify();
    }

    @Test void backendFailureIsNotPresentedAsASuccessOrLeaked() throws Exception {
        authenticate("Admin");
        fixture.server().expect(requestTo("http://backend.test/api/v1/messaging/listeners/receptionNotifications/pause"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("private backend error"));
        mvc.perform(post(BASE + "/receptionNotifications/pause").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.detail").value("No se pudo completar la solicitud; vuelve a intentarlo"));
        fixture.server().verify();
    }

    @Test void unauthorizedRequestsNeverReachBackend() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/receptionNotifications/pause")).andExpect(status().isUnauthorized());
        for (String role : List.of("Huesped", "Auditor", "Recepcionista")) {
            authenticate(role);
            if (!role.equals("Recepcionista")) mvc.perform(get(BASE).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
            for (String action : List.of("pause", "resume")) {
                mvc.perform(post(BASE + "/receptionNotifications/" + action).header("Authorization", "Bearer test-token"))
                        .andExpect(status().isForbidden());
            }
        }
        authenticate("Admin");
        mvc.perform(delete(BASE + "/receptionNotifications").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        fixture.server().verify();
    }

    private void authenticate(String role) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token").header("alg", "RS256")
                .subject("test-user").claim("roles", List.of(role)).build());
    }
}
