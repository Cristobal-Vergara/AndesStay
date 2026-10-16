package com.example.AndesStayRabbitAdmin;

import com.example.AndesStayRabbitAdmin.config.SecurityConfig;
import com.example.AndesStayRabbitAdmin.controller.RabbitAdminController;
import com.example.AndesStayRabbitAdmin.dto.RabbitRequests.*;
import com.example.AndesStayRabbitAdmin.dto.RabbitResponses.*;
import com.example.AndesStayRabbitAdmin.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RabbitAdminController.class)
@Import(SecurityConfig.class)
class RabbitAdminApiTests {
    static final String Q = "andesstay.ops.queue";
    static final String X = "andesstay.ops.exchange";
    static final String QUEUE_JSON = "{\"name\":\"andesstay.ops.queue\",\"settings\":{\"messageTtlMs\":60000,\"maxLength\":100}}";
    @Autowired MockMvc mvc;
    @MockitoBean RabbitAdminService service;
    @MockitoBean JwtDecoder decoder;

    @Test void everyVerbRequiresAuthentication() throws Exception {
        for (String method : List.of("GET", "POST", "PUT", "DELETE"))
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/api/v1/rabbitmq/queues").contentType(MediaType.APPLICATION_JSON).content(QUEUE_JSON))
                    .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"Huesped", "Recepcionista", "Auditor"})
    void otherRolesCannotMutateOrRead(String role) throws Exception {
        mvc.perform(post("/api/v1/rabbitmq/queues").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON).content(QUEUE_JSON)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/rabbitmq/queues").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/rabbitmq", "/api/rabbitmq"})
    void bearerTokenRolesClaimIsConvertedByTheRealSecurityChain(String base) throws Exception {
        when(decoder.decode("admin-test")).thenReturn(Jwt.withTokenValue("admin-test").header("alg", "RS256")
                .subject("test-admin").claim("roles", List.of("Admin")).build());
        when(service.queues(1, 25)).thenReturn(new Page<>(List.of(), 1, 25, 0));
        mvc.perform(get(base + "/queues").header("Authorization", "Bearer admin-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pageSize").value(25));
        verify(decoder).decode("admin-test");
    }

    @Test void crudReturnsLocationsAndHttpStatusCodes() throws Exception {
        when(service.createQueue(any())).thenReturn(new QueueView(Q, true, false, 0L, 0L, 0, Map.of(), true));
        when(service.createExchange(any())).thenReturn(new ExchangeView(X, "topic", true, Map.of(), true));
        when(service.createBinding(any())).thenReturn(new BindingView(X, Q, "reservation.#", "opaque", true));
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"));
        mvc.perform(post("/api/v1/rabbitmq/queues").with(admin).contentType(MediaType.APPLICATION_JSON).content(QUEUE_JSON))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/rabbitmq/queues/" + Q));
        mvc.perform(post("/api/v1/rabbitmq/exchanges").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + X + "\",\"type\":\"topic\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/rabbitmq/exchanges/" + X));
        mvc.perform(post("/api/v1/rabbitmq/bindings").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"exchange\":\"" + X + "\",\"queue\":\"" + Q + "\",\"routingKey\":\"reservation.#\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location",
                        "/api/v1/rabbitmq/bindings/" + X + "/" + Q + "?routingKey=reservation.%23"));
        mvc.perform(put("/api/v1/rabbitmq/queues/" + Q).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageTtlMs\":1000,\"maxLength\":5}")).andExpect(status().isOk());
        for (String path : List.of("/queues/" + Q, "/exchanges/" + X))
            mvc.perform(delete("/api/v1/rabbitmq" + path).with(admin)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/rabbitmq/bindings/" + X + "/" + Q).param("routingKey", "reservation.#").with(admin))
                .andExpect(status().isNoContent());
        verify(service).deleteQueue(Q);
        verify(service).deleteExchange(X);
        verify(service).deleteBinding(X, Q, "reservation.#");
    }

    @Test void legacyCreationReturnsAUsableVersionOneLocation() throws Exception {
        when(service.createQueue(any())).thenReturn(new QueueView(Q, true, false, 0L, 0L, 0, Map.of(), true));
        when(service.queue(Q)).thenReturn(new QueueView(Q, true, false, 0L, 0L, 0, Map.of(), true));
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"));
        var result = mvc.perform(post("/api/rabbitmq/queues").with(admin)
                .contentType(MediaType.APPLICATION_JSON).content(QUEUE_JSON))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/rabbitmq/queues/" + Q))
                .andReturn();
        mvc.perform(get(result.getResponse().getHeader("Location")).with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(Q));
    }

    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"name\":\"../queue\",\"settings\":{\"messageTtlMs\":1000,\"maxLength\":5}}",
        "{\"name\":\"andesstay.ops.queue\",\"settings\":{\"messageTtlMs\":0,\"maxLength\":5}}",
        "{\"name\":\"andesstay.ops.queue\",\"settings\":{\"messageTtlMs\":1000,\"maxLength\":0}}",
        "{\"name\":\"andesstay.ops.queue\",\"settings\":{\"messageTtlMs\":604800001,\"maxLength\":5}}",
        "{\"name\":\"andesstay.ops.queue\",\"settings\":{\"messageTtlMs\":1000.5,\"maxLength\":5}}",
        "{\"name\":\"andesstay.ops.queue\",\"settings\":{\"messageTtlMs\":1000,\"maxLength\":5},\"admin\":true}",
        "{\"name\":\"andesstay.ops.queue\",\"settings\":{\"messageTtlMs\":1000,\"maxLength\":5,\"durable\":false}}"
    })
    void invalidBodiesNeverReachTheService(String body) throws Exception {
        mvc.perform(post("/api/v1/rabbitmq/queues").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"/queues?page=0", "/queues?pageSize=101", "/queues?pageSize=0",
            "/queues/BadName", "/bindings?exchange=bad:name&queue=andesstay.ops.queue",
            "/bindings/andesstay.ops.exchange/andesstay.ops.queue?routingKey=bad:key"})
    void invalidQueryAndPathParametersAreRejected(String path) throws Exception {
        mvc.perform(get("/api/v1/rabbitmq" + path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void serviceErrorsKeepTheirStatusAndSafeCode() throws Exception {
        for (var http : List.of(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE)) {
            doThrow(new RabbitAdminException(http, "SAFE_CODE", "Mensaje seguro")).when(service).queue(Q);
            mvc.perform(get("/api/v1/rabbitmq/queues/" + Q).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                    .andExpect(status().is(http.value())).andExpect(jsonPath("$.code").value("SAFE_CODE"))
                    .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.stackTrace").doesNotExist());
        }
    }
}
