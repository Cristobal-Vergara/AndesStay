package com.example.andesstay.AndesStayBFF.config;

import com.example.andesstay.AndesStayBFF.controller.BffController;
import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(value=BffController.class, properties="backend.url=http://backend.test")
@Import({SecurityConfig.class,BffReservationService.class,AuditProxyTests.ClientConfiguration.class})
class MessagingProxyTests {
    @Autowired MockMvc mvc;
    @Autowired AuditProxyTests.ClientFixture fixture;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup() { fixture.server().reset(); }
    @Test void forwardsVerifiedTokenForStatus() throws Exception {
        authenticate("Recepcionista");
        fixture.server().expect(requestTo("http://backend.test/api/v1/messaging/status"))
            .andExpect(header("Authorization","Bearer test-token"))
            .andRespond(withSuccess("{\"available\":false,\"pending\":null}",MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/v1/bff/messaging/status").header("Authorization","Bearer test-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        fixture.server().verify();
    }
    @Test void demoPublishesToFixedPathWithoutForwardingClientPayload() throws Exception {
        authenticate("Admin");
        fixture.server().expect(requestTo("http://backend.test/api/v1/messaging/demo"))
            .andExpect(method(HttpMethod.POST)).andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(""))
            .andExpect(header("Authorization","Bearer test-token"))
            .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body("{\"eventId\":\"safe\"}"));
        mvc.perform(post("/api/v1/bff/messaging/demo").header("Authorization","Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"queue\":\"forged\",\"payload\":\"forged\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.eventId").value("safe"));
        fixture.server().verify();
    }
    @Test void unauthorizedRolesCannotContactBackend() throws Exception {
        mvc.perform(get("/api/v1/bff/messaging/status")).andExpect(status().isUnauthorized());
        for(String role:List.of("Huesped","Auditor")) {
            authenticate(role);
            mvc.perform(get("/api/v1/bff/messaging/status").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        }
        authenticate("Recepcionista");
        mvc.perform(post("/api/v1/bff/messaging/demo").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        fixture.server().verify();
    }
    private void authenticate(String role) {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token").header("alg","none")
            .subject("user").claim("roles",List.of(role)).build());
    }
}
