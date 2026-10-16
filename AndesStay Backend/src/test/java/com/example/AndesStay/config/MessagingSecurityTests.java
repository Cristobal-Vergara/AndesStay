package com.example.AndesStay.config;

import com.example.AndesStay.controller.MessagingController;
import com.example.AndesStay.service.*;
import com.example.AndesStay.dto.MessagingStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessagingController.class)
@Import(SecurityConfig.class)
class MessagingSecurityTests {
    @MockitoBean com.example.AndesStay.repository.UserProfileRepository profiles;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean MessagingStatusService service;
    @MockitoBean MessagingDemoService demo;
    @Autowired MockMvc mvc;

    @ParameterizedTest @CsvSource({"Admin,200", "Recepcionista,200", "Huesped,403", "Auditor,403"})
    void statusIsRestrictedToOperations(String role, int expected) throws Exception {
        authenticate(role);
        when(service.status()).thenReturn(new MessagingStatusResponse(true,2L,1L,1L,1L,true,0L,Instant.EPOCH));
        mvc.perform(get("/api/v1/messaging/status").header("Authorization","Bearer test")).andExpect(status().is(expected));
        if(expected==200) verify(service).status(); else verifyNoInteractions(service);
    }
    @ParameterizedTest @CsvSource({"Admin,202", "Recepcionista,403", "Huesped,403", "Auditor,403"})
    void onlyAdminCanSendAnIsolatedDemo(String role,int expected) throws Exception {
        authenticate(role); when(demo.publish()).thenReturn("controlled");
        mvc.perform(post("/api/v1/messaging/demo").header("Authorization","Bearer test"))
            .andExpect(status().is(expected));
        if(expected==202) verify(demo).publish(); else verifyNoInteractions(demo);
    }
    @Test void anonymousAndOtherMethodsAreDenied() throws Exception {
        mvc.perform(get("/api/v1/messaging/status")).andExpect(status().isUnauthorized());
        authenticate("Admin");
        mvc.perform(delete("/api/v1/messaging/status").header("Authorization","Bearer test")).andExpect(status().isForbidden());
        verifyNoInteractions(service,demo);
    }
    private void authenticate(String role) {
        when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg","none")
            .subject("user").claim("roles",List.of(role)).build());
    }
}
