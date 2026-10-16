package com.example.AndesStayRabbitAdmin;

import com.example.AndesStayRabbitAdmin.client.RabbitManagementClient;
import com.example.AndesStayRabbitAdmin.client.RabbitManagementClient.*;
import com.example.AndesStayRabbitAdmin.dto.RabbitRequests.*;
import com.example.AndesStayRabbitAdmin.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTests {
    static final String Q = "andesstay.ops.queue";
    static final String X = "andesstay.ops.exchange";
    @Mock RabbitManagementClient broker;
    RabbitAdminService service;

    @BeforeEach void setUp() {
        service = new RabbitAdminService(broker, new RabbitResourceScope("andesstay.ops.", "reservas",
                Set.of("reservas", "andesstay.ops.protected")));
    }
    BrokerQueue queue() { return new BrokerQueue(Q, true, false, 0L, 0L, 0, Map.of()); }
    BrokerExchange exchange() { return new BrokerExchange(X, "topic", true); }
    BrokerBinding binding(String key, String opaque) { return new BrokerBinding(X, Q, key, opaque, Map.of()); }
    RabbitAdminException conflict() { return new RabbitAdminException(HttpStatus.CONFLICT, "BROKER_CONFLICT", "En uso"); }

    @Test void everyMutationRejectsProtectedResourcesBeforeContactingTheBroker() {
        var settings = new QueueSettings(60000, 100, null);
        List<Runnable> operations = List.of(
                () -> service.createQueue(new QueueCreate("reservas", settings)),
                () -> service.updateQueue("reservas", settings),
                () -> service.deleteQueue("andesstay.ops.protected"),
                () -> service.createExchange(new ExchangeCreate("reservas", "topic")),
                () -> service.updateExchange("reservas", new ExchangeSettings(null)),
                () -> service.deleteExchange("reservas"),
                () -> service.createBinding(new BindingCreate(X, "reservas", "key")),
                () -> service.updateBinding("reservas", Q, "old", new BindingSettings("new")),
                () -> service.deleteBinding(X, "reservas", "key"));
        for (Runnable operation : operations)
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(RabbitAdminException.class, operation::run).status());
        verifyNoInteractions(broker);
    }

    @Test void queueUpdateUsesAnExactPolicyWithoutRecreatingTheQueue() {
        when(broker.queue(Q)).thenReturn(queue());
        service.updateQueue(Q, new QueueSettings(60000, 100, null));
        verify(broker).putPolicy(Q + ".queue-policy", "^andesstay\\.ops\\.queue$", "queues",
                Map.of("message-ttl", 60000, "max-length", 100));
        verify(broker, never()).declareQueue(anyString());
        verify(broker, never()).deleteQueue(anyString());
    }

    @Test void queueCreationValidatesItsDlxAndAddsItsOwnPolicy() {
        when(broker.exchange(X)).thenReturn(exchange());
        when(broker.findQueue(Q)).thenReturn(Optional.empty());
        when(broker.queue(Q)).thenReturn(queue());
        service.createQueue(new QueueCreate(Q, new QueueSettings(1000, 5, X)));
        var order = inOrder(broker);
        order.verify(broker).exchange(X);
        order.verify(broker).findQueue(Q);
        order.verify(broker).declareQueue(Q);
        order.verify(broker).putPolicy(Q + ".queue-policy", "^andesstay\\.ops\\.queue$", "queues",
                Map.of("message-ttl", 1000, "max-length", 5, "dead-letter-exchange", X));
    }

    @Test void queueCannotUseTheReservationsDlx() {
        when(broker.queue(Q)).thenReturn(queue());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(RabbitAdminException.class,
                () -> service.updateQueue(Q, new QueueSettings(1000, 5, "reservas"))).status());
        verify(broker, never()).putPolicy(anyString(), anyString(), anyString(), anyMap());
    }

    @Test void duplicateQueueIsAConflict() {
        when(broker.findQueue(Q)).thenReturn(Optional.of(queue()));
        assertEquals(HttpStatus.CONFLICT, assertThrows(RabbitAdminException.class,
                () -> service.createQueue(new QueueCreate(Q, new QueueSettings(1000, 5, null)))).status());
        verify(broker, never()).declareQueue(anyString());
    }

    @Test void failedQueueCreationCompensatesOnlyWithConditionalDelete() {
        when(broker.findQueue(Q)).thenReturn(Optional.empty());
        doThrow(conflict()).when(broker).putPolicy(anyString(), anyString(), anyString(), anyMap());
        assertThrows(RabbitAdminException.class,
                () -> service.createQueue(new QueueCreate(Q, new QueueSettings(1000, 5, null))));
        verify(broker).deleteQueue(Q);
        verify(broker).deletePolicyIfPresent(Q + ".queue-policy");
    }

    @Test void failedCompensationPreservesThePolicyAndOriginalFailure() {
        when(broker.findQueue(Q)).thenReturn(Optional.empty());
        var original = conflict();
        doThrow(original).when(broker).putPolicy(anyString(), anyString(), anyString(), anyMap());
        doThrow(conflict()).when(broker).deleteQueue(Q);
        assertSame(original, assertThrows(RabbitAdminException.class,
                () -> service.createQueue(new QueueCreate(Q, new QueueSettings(1000, 5, null)))));
        assertEquals(1, original.getSuppressed().length);
        verify(broker, never()).deletePolicyIfPresent(anyString());
    }

    @Test void busyQueueDeletionDoesNotRemoveItsPolicy() {
        when(broker.queue(Q)).thenReturn(queue());
        doThrow(conflict()).when(broker).deleteQueue(Q);
        assertThrows(RabbitAdminException.class, () -> service.deleteQueue(Q));
        verify(broker, never()).deletePolicyIfPresent(anyString());
    }

    @Test void missingReservationQueueDoesNotReportAHealthyStatus() {
        when(broker.queue("reservas")).thenThrow(new RabbitAdminException(HttpStatus.NOT_FOUND, "MISSING", "No existe"));
        var failure = assertThrows(RabbitAdminException.class, service::status);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status());
        assertEquals("TOPOLOGY_UNAVAILABLE", failure.code());
    }

    @Test void exchangeUpdateRejectsSelfReferenceWithoutInstallingAPolicy() {
        when(broker.exchange(X)).thenReturn(exchange());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(RabbitAdminException.class,
                () -> service.updateExchange(X, new ExchangeSettings(X))).status());
        verify(broker, never()).putPolicy(anyString(), anyString(), anyString(), anyMap());
    }

    @Test void exchangeStillPresentAfterConditionalDeletionReturnsConflict() {
        when(broker.exchange(X)).thenReturn(exchange());
        when(broker.policies()).thenReturn(List.of());
        when(broker.findExchange(X)).thenReturn(Optional.of(exchange()));
        assertEquals(HttpStatus.CONFLICT, assertThrows(RabbitAdminException.class, () -> service.deleteExchange(X)).status());
        verify(broker, never()).deletePolicyIfPresent(anyString());
    }

    @Test void exchangeReferencedByAnOperationalPolicyCannotBeDeleted() {
        when(broker.exchange(X)).thenReturn(exchange());
        when(broker.policies()).thenReturn(List.of(new BrokerPolicy(Q + ".queue-policy", Map.of("dead-letter-exchange", X))));
        assertThrows(RabbitAdminException.class, () -> service.deleteExchange(X));
        verify(broker, never()).deleteExchange(anyString());
    }

    @Test void bindingUpdateDeclaresTheNewRouteBeforeDeletingTheOpaqueOldId() {
        when(broker.bindings(X, Q)).thenReturn(List.of(binding("old", "opaque-old")),
                List.of(binding("old", "opaque-old")), List.of(binding("new.#", "opaque-new")));
        var result = service.updateBinding(X, Q, "old", new BindingSettings("new.#"));
        var order = inOrder(broker);
        order.verify(broker).declareBinding(X, Q, "new.#");
        order.verify(broker).deleteBinding(X, Q, "opaque-old");
        assertEquals("opaque-new", result.propertiesKey());
    }

    @Test void bindingUpdateRollsBackOnlyTheRouteItJustCreated() {
        when(broker.bindings(X, Q)).thenReturn(List.of(binding("old", "id-old")),
                List.of(binding("old", "id-old")), List.of(binding("new", "id-new")));
        doThrow(conflict()).when(broker).deleteBinding(X, Q, "id-old");
        assertThrows(RabbitAdminException.class, () -> service.updateBinding(X, Q, "old", new BindingSettings("new")));
        verify(broker).deleteBinding(X, Q, "id-new");
    }

    @Test void bindingUpdatePreservesAnAlreadyExistingTargetWhenOldDeletionFails() {
        when(broker.bindings(X, Q)).thenReturn(List.of(binding("old", "id-old"), binding("new", "id-new")));
        doThrow(conflict()).when(broker).deleteBinding(X, Q, "id-old");
        assertThrows(RabbitAdminException.class, () -> service.updateBinding(X, Q, "old", new BindingSettings("new")));
        verify(broker, never()).declareBinding(anyString(), anyString(), anyString());
        verify(broker, never()).deleteBinding(X, Q, "id-new");
    }

    @Test void bindingsWithArgumentsAreReadOnlyAndCannotBeConfusedWithTheManagedRoute() {
        when(broker.bindings(X, Q)).thenReturn(List.of(new BrokerBinding(X, Q, "key", "id", Map.of("special", 1))));
        assertFalse(service.bindings(X, Q).get(0).writable());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(RabbitAdminException.class,
                () -> service.deleteBinding(X, Q, "key")).status());
        verify(broker, never()).deleteBinding(anyString(), anyString(), anyString());
    }
}
