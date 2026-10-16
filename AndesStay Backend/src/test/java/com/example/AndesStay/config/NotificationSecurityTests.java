package com.example.AndesStay.config;

import com.example.AndesStay.controller.ReservationNotificationController;
import com.example.AndesStay.service.ReservationNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationNotificationController.class)
@Import(SecurityConfig.class)
class NotificationSecurityTests {
    @MockitoBean com.example.AndesStay.repository.UserProfileRepository profiles;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean ReservationNotificationService service;
    @Autowired MockMvc mvc;

    @ParameterizedTest @CsvSource({"Admin,200", "Recepcionista,200", "Huesped,200", "Auditor,403"})
    void onlyBusinessRolesCanRead(String role, int expected) throws Exception {
        authenticate(role);
        when(service.list(2,5)).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/notifications").header("Authorization","Bearer test")
            .param("page","2").param("size","5").param("recipientId","forged"))
            .andExpect(status().is(expected));
        if (expected == 200) verify(service).list(2,5); else verifyNoInteractions(service);
    }
    @Test void unauthenticatedAndManualPublicationAreRejected() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        authenticate("Admin");
        mvc.perform(post("/api/v1/notifications").header("Authorization","Bearer test")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    private void authenticate(String role) {
        when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg","none")
            .subject("user").claim("roles",List.of(role)).build());
    }
}
