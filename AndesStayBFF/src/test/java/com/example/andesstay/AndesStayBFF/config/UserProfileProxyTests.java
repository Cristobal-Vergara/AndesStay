package com.example.andesstay.AndesStayBFF.config;

import com.example.andesstay.AndesStayBFF.controller.BffController;
import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=BffController.class,properties="backend.url=http://backend.test")
@Import({SecurityConfig.class,BffReservationService.class,AuditProxyTests.ClientConfiguration.class})
class UserProfileProxyTests {
    @Autowired MockMvc mvc;
    @Autowired AuditProxyTests.ClientFixture fixture;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup() {
        fixture.server().reset();
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token").header("alg","RS256").subject("object").build());
    }
    @Test void anonymousCannotReadOrRegisterAndBackendIsNotCalled() throws Exception {
        mvc.perform(get("/api/v1/bff/account/profile")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/bff/account/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        fixture.server().verify();
    }
    @Test void profileReadWithoutRolesUsesValidatedBearerAndFixedPath() throws Exception {
        fixture.server().expect(requestTo("http://backend.test/api/v1/account/profile"))
            .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization","Bearer test-token"))
            .andRespond(withSuccess("{\"registered\":false}",MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/v1/bff/account/profile").header("Authorization","Bearer test-token").param("ownerKey","victim"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(false));
        fixture.server().verify();
    }
    @Test void registrationForwardsJsonAndValidatedToken() throws Exception {
        fixture.server().expect(requestTo("http://backend.test/api/v1/account/register"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer test-token"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"displayName\":\"Ana Torres\"}"))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"registered\":true}"));
        mvc.perform(post("/api/v1/bff/account/register").header("Authorization","Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ana Torres\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.registered").value(true));
        fixture.server().verify();
    }
    @Test void duplicateKeepsConflictStatusAndSafeExplanation() throws Exception {
        fixture.server().expect(requestTo("http://backend.test/api/v1/account/register"))
            .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"status\":409,\"detail\":\"Tu cuenta ya tiene un perfil en AndesStay.\"}"));
        mvc.perform(post("/api/v1/bff/account/register").header("Authorization","Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ana Torres\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Tu cuenta ya tiene un perfil en AndesStay."));
        fixture.server().verify();
    }
    @Test void profileAccessDoesNotAllowOtherOperationsWithoutRoles() throws Exception {
        mvc.perform(get("/api/v1/bff/reservations").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bff/catalog").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        fixture.server().verify();
    }
}
