package com.example.AndesStayRabbitAdmin;

import com.example.AndesStayRabbitAdmin.config.SecurityConfig;
import com.example.AndesStayRabbitAdmin.controller.RabbitAdminController;
import org.junit.jupiter.api.Test;
import com.example.AndesStayRabbitAdmin.config.RabbitManagementConfig;
import com.example.AndesStayRabbitAdmin.client.RabbitManagementClient;
import com.example.AndesStayRabbitAdmin.client.RabbitManagementClient.BrokerQueue;
import com.example.AndesStayRabbitAdmin.service.RabbitAdminService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RabbitAdminController.class)
@Import({SecurityConfig.class, RabbitManagementConfig.class, RabbitAdminService.class})
@TestPropertySource(properties = "andesstay.rabbitmq.reservation-queue=verify.renamed.reception")
class RabbitTopologyControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean RabbitManagementClient broker;
    @MockitoBean JwtDecoder decoder;

    @Test void statusQueriesTheConfiguredQueueInsteadOfAnEmbeddedName() throws Exception {
        when(broker.queue("verify.renamed.reception")).thenReturn(
                new BrokerQueue("verify.renamed.reception", true, false, 0L, 0L, 1, Map.of()));
        mvc.perform(get("/api/rabbitmq/status").with(jwt()
            .authorities(new SimpleGrantedAuthority("ROLE_Admin")))).andExpect(status().isOk());
        verify(broker).queue("verify.renamed.reception");
    }
}
