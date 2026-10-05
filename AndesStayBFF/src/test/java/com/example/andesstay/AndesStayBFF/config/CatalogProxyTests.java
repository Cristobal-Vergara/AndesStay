package com.example.andesstay.AndesStayBFF.config;
import com.example.andesstay.AndesStayBFF.controller.BffController;
import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=BffController.class,properties="backend.url=http://backend.test")
@Import({SecurityConfig.class,BffReservationService.class,AuditProxyTests.ClientConfiguration.class})
class CatalogProxyTests {
    @Autowired MockMvc mvc;
    @Autowired AuditProxyTests.ClientFixture fixture;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup(){fixture.server().reset();authenticate("Recepcionista");}
    @Test void paginatedCatalogAndValidatedBearerTokenReachBackend() throws Exception {
        authenticate("Huesped");
        fixture.server().expect(requestTo("http://backend.test/api/catalog/units?page=2&size=10"))
            .andExpect(header("Authorization","Bearer test-token"))
            .andRespond(withSuccess("{\"content\":[],\"page\":2,\"size\":10,\"totalElements\":0,\"totalPages\":0}",MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/bff/catalog").header("Authorization","bearer test-token").param("page","2").param("size","10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2));
        fixture.server().verify();
    }
    @Test void catalogCreationForwardsBodyAndReturnsCreated() throws Exception {
        String body="{\"name\":\"Nueva\"}";
        fixture.server().expect(requestTo("http://backend.test/api/catalog/units")).andExpect(method(HttpMethod.POST))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(body)).andRespond(withSuccess("{\"id\":3}",MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/bff/catalog").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(3));
        fixture.server().verify();
    }
    @Test void assignmentForwardsOnlySuppliedBodyToFixedReservationPath() throws Exception {
        String body="{\"objectId\":\"33333333-3333-3333-3333-333333333333\"}";
        fixture.server().expect(requestTo("http://backend.test/api/reservations/9/owner")).andExpect(method(HttpMethod.PUT))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(body)).andRespond(withSuccess("{\"id\":9,\"guestAssigned\":true}",MediaType.APPLICATION_JSON));
        mvc.perform(put("/api/bff/reservations/9/owner").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.guestAssigned").value(true));
        fixture.server().verify();
    }
    @Test void catalogDeletePreservesConflictExplanation() throws Exception {
        fixture.server().expect(requestTo("http://backend.test/api/catalog/units/3")).andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON).body("{\"status\":409,\"detail\":\"Unidad con reservas\"}"));
        mvc.perform(delete("/api/bff/catalog/3").header("Authorization","Bearer test-token"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Unidad con reservas"));
        fixture.server().verify();
    }
    @Test void guestCannotAssignOrCreateCatalogAndDoesNotContactBackend() throws Exception {
        authenticate("Huesped");
        mvc.perform(put("/api/bff/reservations/9/owner").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/bff/catalog").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        fixture.server().verify();
    }
    private void authenticate(String role) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token").header("alg","none").subject("user").claim("roles",List.of(role)).build());
    }
}
