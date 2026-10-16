package com.example.AndesStayKafkaAdmin;

import com.example.AndesStayKafkaAdmin.config.SecurityConfig;
import com.example.AndesStayKafkaAdmin.controller.KafkaAdminController;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KafkaAdminController.class)
@Import(SecurityConfig.class)
class KafkaApiVersioningTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean AdminClient adminClient;

    @ParameterizedTest @ValueSource(strings = {"/api/v1/kafka/status", "/api/kafka/status"})
    void administratorCanUseBothRoutes(String path) throws Exception {
        authenticate("Admin");
        mvc.perform(get(path).header("Authorization", "Bearer version-test"))
                .andExpect(status().isOk()).andExpect(content().string("Kafka Admin funcionando"));
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/kafka/status", "/api/kafka/status"})
    void bothRoutesRejectAnonymousAndNonAdminUsers(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String role : List.of("Huesped", "Recepcionista", "Auditor")) {
            authenticate(role);
            mvc.perform(get(path).header("Authorization", "Bearer version-test"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(adminClient);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/kafka/status", "/api/kafka/status"})
    void invalidTokensAreRejected(String path) throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid test token"));
        mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminClient);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v2/kafka/status", "/api/v1x/kafka/status"})
    void unconfiguredVersionsAreNotRouted(String path) throws Exception {
        authenticate("Admin");
        mvc.perform(get(path).header("Authorization", "Bearer version-test")).andExpect(status().isNotFound());
        verifyNoInteractions(adminClient);
    }

    private void authenticate(String role) {
        when(decoder.decode("version-test")).thenReturn(Jwt.withTokenValue("version-test")
                .header("alg", "RS256").subject("test-user").claim("roles", List.of(role)).build());
    }
}
