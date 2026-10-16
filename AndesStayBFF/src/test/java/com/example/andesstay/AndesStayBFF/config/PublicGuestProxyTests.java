package com.example.andesstay.AndesStayBFF.config;

import com.example.andesstay.AndesStayBFF.controller.BffController;
import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=BffController.class,properties="backend.url=http://backend.test")
@Import({SecurityConfig.class,BffReservationService.class,AuditProxyTests.ClientConfiguration.class})
class PublicGuestProxyTests {
    @Autowired MockMvc mvc;
    @Autowired AuditProxyTests.ClientFixture fixture;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup() {
        fixture.server().reset();
        authenticate(true,null);
    }
    @Test void sameTokenGetsGuestAccessImmediatelyAfterRegistration() throws Exception {
        profile("{\"registered\":false,\"roles\":[]}");
        fixture.server().expect(requestTo("http://backend.test/api/v1/account/register"))
            .andExpect(header("Authorization","Bearer public"))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"registered\":true,\"roles\":[\"Huesped\"]}"));
        profile("{\"registered\":true,\"roles\":[\"Huesped\"]}");
        fixture.server().expect(requestTo("http://backend.test/api/v1/reservations"))
            .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization","Bearer public"))
            .andRespond(withSuccess("[]",MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/v1/bff/account/register").header("Authorization","Bearer public")
            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ana Torres\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("Huesped"));
        mvc.perform(get("/api/v1/bff/reservations").header("Authorization","Bearer public")).andExpect(status().isOk());
        fixture.server().verify();
    }
    @Test void registeredCustomerCanCreateButCannotManageReservationsOrReadReports() throws Exception {
        profile("{\"registered\":true,\"roles\":[\"Huesped\"]}");
        fixture.server().expect(requestTo("http://backend.test/api/v1/reservations"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer public"))
            .andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        var managementPaths=List.of("/api/v1/bff/reservations/1/status","/api/v1/bff/catalog/1");
        var staffPaths=List.of("/api/v1/bff/reports","/api/v1/bff/audit");
        for(String ignored:managementPaths) profile("{\"registered\":true,\"roles\":[\"Huesped\"]}");
        for(String ignored:staffPaths) profile("{\"registered\":true,\"roles\":[\"Huesped\",\"Admin\"]}");

        mvc.perform(post("/api/v1/bff/reservations").header("Authorization","Bearer public")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        for(String path:managementPaths) {
            mvc.perform(put(path).header("Authorization","Bearer public").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        }
        for(String path:staffPaths) {
            mvc.perform(get(path).header("Authorization","Bearer public")).andExpect(status().isForbidden());
        }
        fixture.server().verify();
    }
    @ParameterizedTest @ValueSource(strings={
        "{\"registered\":false,\"roles\":[\"Huesped\"]}",
        "{\"registered\":true,\"roles\":[\"Admin\"]}",
        "{\"registered\":\"true\",\"roles\":[\"Huesped\"]}",
        "{\"registered\":true,\"roles\":\"Huesped\"}", "{}","invalid-json"})
    void absentOrMalformedProfileNeverGrantsAccess(String body) throws Exception {
        profile(body);
        mvc.perform(get("/api/v1/bff/reservations").header("Authorization","Bearer public")).andExpect(status().isForbidden());
        fixture.server().verify();
    }
    @Test void backendFailureNeverGrantsGuestAccess() throws Exception {
        fixture.server().expect(requestTo("http://backend.test/api/v1/account/profile")).andRespond(withServerError().body("Internal data"));
        mvc.perform(get("/api/v1/bff/reservations").header("Authorization","Bearer public")).andExpect(status().isForbidden());
        fixture.server().verify();
    }
    @Test void applicationTokenWithoutDelegatedScopeIsNotTreatedAsCustomer() throws Exception {
        authenticate(false,null);
        mvc.perform(get("/api/v1/bff/reservations").header("Authorization","Bearer public")).andExpect(status().isForbidden());
        fixture.server().verify();
    }
    @Test void auditorKeepsOnlyItsAssignedPermissionsWithoutProfileLookup() throws Exception {
        authenticate(true,"Auditor");
        mvc.perform(get("/api/v1/bff/reservations").header("Authorization","Bearer public")).andExpect(status().isForbidden());
        fixture.server().verify();
    }
    private void profile(String body) {
        fixture.server().expect(requestTo("http://backend.test/api/v1/account/profile"))
            .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization","Bearer public"))
            .andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
    }
    private void authenticate(boolean delegated,String role) {
        var builder=Jwt.withTokenValue("public").header("alg","RS256").subject("customer")
            .claim("tid","tenant").claim("oid","customer");
        if(delegated) builder.claim("scp","access_as_user");
        if(role!=null) builder.claim("roles",List.of(role));
        when(decoder.decode("public")).thenReturn(builder.build());
    }
}
