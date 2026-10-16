package com.example.andesstay.AndesStayBFF.config;

import com.example.andesstay.AndesStayBFF.controller.BffController;
import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = BffController.class, properties = "backend.url=http://backend.test")
@Import({SecurityConfig.class, BffReservationService.class, AuditProxyTests.ClientConfiguration.class})
class AuditProxyTests {

    @Autowired private MockMvc mvc;
    @Autowired private ClientFixture fixture;
    @MockitoBean private JwtDecoder decoder;

    @BeforeEach
    void resetServer() {
        fixture.server().reset();
    }

    @ParameterizedTest
    @CsvSource({"Admin,200", "Auditor,200", "Recepcionista,403", "Huesped,403", "SinRol,403"})
    void onlyAdminAndAuditorCanReadEvents(String role, int code) throws Exception {
        authenticate(role);
        if (code == 200) expectSuccess("http://backend.test/api/v1/audit");

        mvc.perform(get("/api/v1/bff/audit").header("Authorization", "Bearer test-token"))
                .andExpect(status().is(code));
        fixture.server().verify();
    }

    @Test
    void anonymousRequestRequiresAuthenticationWithoutCallingBackend() throws Exception {
        mvc.perform(get("/api/v1/bff/audit")).andExpect(status().isUnauthorized());
        fixture.server().verify();
    }

    @Test
    void filtersAndBearerTokenReachBackendWithValuesEncoded() throws Exception {
        authenticate("Auditor");
        expectSuccess("http://backend.test/api/v1/audit"
                + "?user=ana%2Bventas%40ejemplo.cl%20%26%20equipo"
                + "&action=RESERVA_CONFIRMADA&inicio=2026-10-02T10%3A00&fin=2026-10-03T10%3A00");

        mvc.perform(get("/api/v1/bff/audit").header("Authorization", "Bearer test-token")
                .param("user", " ana+ventas@ejemplo.cl & equipo ")
                .param("action", "RESERVA_CONFIRMADA")
                .param("inicio", "2026-10-02T10:00").param("fin", "2026-10-03T10:00"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
        fixture.server().verify();
    }

    @ParameterizedTest
    @CsvSource({"inicio,2026-10-02T10:00", "fin,2026-10-03T10:00"})
    void aSingleDateBoundIsForwarded(String field, String value) throws Exception {
        authenticate("Admin");
        expectSuccess("http://backend.test/api/v1/audit?" + field + "=" + value.replace(":", "%3A"));

        mvc.perform(get("/api/v1/bff/audit").header("Authorization", "Bearer test-token").param(field, value))
                .andExpect(status().isOk());
        fixture.server().verify();
    }

    @Test
    void emptyFiltersAreOmittedAndUnknownParametersAreNotForwarded() throws Exception {
        authenticate("Admin");
        expectSuccess("http://backend.test/api/v1/audit");

        mvc.perform(get("/api/v1/bff/audit").header("Authorization", "Bearer test-token")
                .param("user", "   ").param("action", "").param("inicio", " ").param("fin", "")
                .param("backend.url", "http://otro-servidor.test"))
                .andExpect(status().isOk());
        fixture.server().verify();
    }

    @Test
    void invalidFilterResponseKeepsItsStatusAndExplanation() throws Exception {
        authenticate("Admin");
        fixture.server().expect(requestTo("http://backend.test/api/v1/audit?inicio=fecha-invalida"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":400,\"detail\":\"El filtro inicio debe ser una fecha y hora válida\"}"));

        mvc.perform(get("/api/v1/bff/audit").header("Authorization", "Bearer test-token")
                .param("inicio", "fecha-invalida"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El filtro inicio debe ser una fecha y hora válida"));
        fixture.server().verify();
    }

    @Test
    void internalBackendErrorReturnsSanitizedBadGateway() throws Exception {
        authenticate("Admin");
        fixture.server().expect(requestTo("http://backend.test/api/v1/audit"))
                .andRespond(withServerError().body("SQL y datos internos de prueba"));

        mvc.perform(get("/api/v1/bff/audit").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("No se pudo completar la solicitud; vuelve a intentarlo"));
        fixture.server().verify();
    }

    private void expectSuccess(String url) {
        fixture.server().expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    }

    private void authenticate(String role) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of(role)).build());
    }

    record ClientFixture(RestClient.Builder builder, MockRestServiceServer server) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class ClientConfiguration {
        @Bean
        ClientFixture clientFixture() {
            RestClient.Builder builder = RestClient.builder();
            return new ClientFixture(builder, MockRestServiceServer.bindTo(builder).build());
        }

        @Bean
        RestClient.Builder restClientBuilder(ClientFixture fixture) {
            return fixture.builder();
        }
    }
}
