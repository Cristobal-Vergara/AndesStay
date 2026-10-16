package com.example.andesstay.AndesStayBFF.config;

import com.example.andesstay.AndesStayBFF.controller.BffController;
import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = BffController.class, properties = "backend.url=http://backend.test")
@Import({SecurityConfig.class, BffReservationService.class, AuditProxyTests.ClientConfiguration.class})
class ApiVersioningProxyTests {
    @Autowired MockMvc mvc;
    @Autowired AuditProxyTests.ClientFixture fixture;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void resetBackend() { fixture.server().reset(); }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/bff/reservations", "/api/bff/reservations"})
    void oldAndNewRoutesForwardTheVerifiedJwtToVersionOne(String path) throws Exception {
        authenticate("Admin");
        fixture.server().expect(requestTo("http://backend.test/api/v1/reservations"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer version-test"))
                .andRespond(withSuccess("{\"content\":[],\"totalElements\":0}", MediaType.APPLICATION_JSON));
        mvc.perform(get(path).header("Authorization", "Bearer version-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        fixture.server().verify();
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/bff/reservations", "/api/bff/reservations"})
    void bothRoutesRequireValidAuthenticationBeforeCallingBackend(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid test token"));
        mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        fixture.server().verify();
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/bff/reservations", "/api/bff/reservations"})
    void unauthorizedRoleCannotReachBackendThroughEitherRoute(String path) throws Exception {
        authenticate("Auditor");
        mvc.perform(get(path).header("Authorization", "Bearer version-test")).andExpect(status().isForbidden());
        fixture.server().verify();
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v2/bff/reservations", "/api/v01/bff/reservations", "/api/v1x/bff/reservations"})
    void unconfiguredVersionsNeverReachBackend(String path) throws Exception {
        authenticate("Admin");
        mvc.perform(get(path).header("Authorization", "Bearer version-test")).andExpect(status().isForbidden());
        fixture.server().verify();
    }

    private void authenticate(String role) {
        when(decoder.decode("version-test")).thenReturn(Jwt.withTokenValue("version-test")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of(role)).build());
    }
}
