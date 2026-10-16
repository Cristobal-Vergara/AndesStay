package com.example.andesstay.AndesStayBFF.config;

import com.example.andesstay.AndesStayBFF.controller.BffController;
import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=BffController.class, properties="backend.url=http://backend.test")
@Import({SecurityConfig.class, BffReservationService.class, AuditProxyTests.ClientConfiguration.class})
class NotificationProxyTests {
    @Autowired MockMvc mvc;
    @Autowired AuditProxyTests.ClientFixture fixture;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup() { fixture.server().reset(); }

    @ParameterizedTest @CsvSource({"Admin", "Recepcionista", "Huesped"})
    void forwardsVerifiedTokenAndPaginationWithoutClientRecipient(String role) throws Exception {
        authenticate(role);
        fixture.server().expect(requestTo("http://backend.test/api/v1/notifications?page=1&size=5"))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andRespond(withSuccess("{\"content\":[],\"page\":1,\"size\":5,\"totalElements\":0,\"totalPages\":0}", MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/v1/bff/notifications").header("Authorization", "Bearer test-token")
            .param("page","1").param("size","5").param("recipientId","another-user"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
        fixture.server().verify();
    }
    @Test void anonymousAuditorAndManualPublicationCannotContactBackend() throws Exception {
        mvc.perform(get("/api/v1/bff/notifications")).andExpect(status().isUnauthorized());
        authenticate("Auditor");
        mvc.perform(get("/api/v1/bff/notifications").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        authenticate("Admin");
        mvc.perform(post("/api/v1/bff/notifications").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        fixture.server().verify();
    }
    private void authenticate(String role) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token").header("alg","none")
            .subject("user").claim("roles", List.of(role)).build());
    }
}
