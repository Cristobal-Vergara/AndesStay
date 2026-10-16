package com.example.AndesStayRabbitAdmin;

import com.example.AndesStayRabbitAdmin.client.RabbitManagementClient;
import com.example.AndesStayRabbitAdmin.service.RabbitAdminException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RabbitManagementClientTests {
    MockRestServiceServer server;
    RabbitManagementClient broker;
    @BeforeEach void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        broker = new RabbitManagementClient(builder.build(), "http://broker:15672", "/");
    }
    @Test void defaultVhostAndOpaqueBindingIdAreEncodedOnce() {
        server.expect(requestTo("http://broker:15672/api/bindings/%2F/e/andesstay.ops.x/q/andesstay.ops.q/a%2Fb%23"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
        broker.deleteBinding("andesstay.ops.x", "andesstay.ops.q", "a/b#");
        server.verify();
    }
    @Test void deleteQueueRequiresEmptyAndUnusedOnTheBroker() {
        server.expect(requestTo("http://broker:15672/api/queues/%2F/andesstay.ops.q?if-empty=true&if-unused=true"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
        broker.deleteQueue("andesstay.ops.q");
        server.verify();
    }
    @Test void paginationUsesTheBrokerAndToleratesAdditionalResponseFields() {
        server.expect(requestTo("http://broker:15672/api/queues/%2F?pagination=true&page=2&page_size=25&sort=name"))
                .andRespond(withSuccess("{\"items\":[{\"name\":\"q\",\"durable\":true,\"auto_delete\":false,\"node\":\"rabbit@local\"}],"
                        + "\"page\":2,\"page_size\":25,\"total_count\":30,\"filtered_count\":30}", MediaType.APPLICATION_JSON));
        var result = broker.queues(2, 25);
        assertEquals(30, result.total());
        assertEquals("q", result.items().get(0).name());
        server.verify();
    }
    @Test void missingQueueIsAnEmptyOptional() {
        server.expect(anything()).andRespond(withResourceNotFound());
        assertTrue(broker.findQueue("absent").isEmpty());
    }
    @ParameterizedTest @ValueSource(ints = {400, 409, 412})
    void brokerConflictsMapTo409(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(RabbitAdminException.class, () -> broker.queue("q")).status());
    }
    @ParameterizedTest @ValueSource(ints = {401, 403, 500, 503})
    void brokerFailuresDoNotExposeCredentialsOrTheirResponseBody(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).body("secret password private-user"));
        var failure = assertThrows(RabbitAdminException.class, () -> broker.queue("q"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status());
        assertFalse(failure.getMessage().contains("secret"));
        assertNull(failure.getCause());
    }
    @Test void managementAddressCannotContainCredentialsOrAnArbitraryPath() {
        for (String url : java.util.List.of("http://user:secret@broker", "file:///tmp", "http://broker/private", "http://broker?token=secret"))
            assertThrows(IllegalArgumentException.class, () -> new RabbitManagementClient(RestClient.create(), url, "/"));
    }
}
