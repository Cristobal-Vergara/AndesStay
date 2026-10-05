package com.example.AndesStay.service;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MessagingStatusTests {
    AmqpAdmin admin=mock(AmqpAdmin.class);
    @SuppressWarnings("unchecked") ObjectProvider<MessagingDemoService> demo=mock(ObjectProvider.class);
    MessagingStatusService service=new MessagingStatusService(admin,new Queue("r"),new Queue("g"),new Queue("d"),demo,
        Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
    @Test void returnsRealCountsWithoutAnyPayload() {
        when(admin.getQueueInfo("r")).thenReturn(new QueueInformation("r",2,1));
        when(admin.getQueueInfo("g")).thenReturn(new QueueInformation("g",3,0));
        when(admin.getQueueInfo("d")).thenReturn(new QueueInformation("d",4,0));
        var status=service.status();
        assertTrue(status.available()); assertEquals(5L,status.pending()); assertEquals(4L,status.quarantined());
        assertEquals(0L,status.guestConsumers()); assertFalse(status.demoEnabled()); assertNull(status.demoQuarantined());
        assertEquals(Instant.EPOCH,status.checkedAt());
    }
    @Test void missingQueueIsUnknownInsteadOfZero() {
        assertFalse(service.status().available()); assertNull(service.status().quarantined());
    }
    @Test void disconnectedBrokerIsUnknownInsteadOfHealthy() {
        when(admin.getQueueInfo("r")).thenThrow(new AmqpConnectException(new java.net.ConnectException()));
        var status=service.status();
        assertFalse(status.available()); assertNull(status.pending()); assertNull(status.receptionConsumers());
    }
    @Test void demoUsesItsOwnQuarantine() {
        var local=mock(MessagingDemoService.class);
        when(demo.getIfAvailable()).thenReturn(local); when(local.deadQueueName()).thenReturn("demo.dlq");
        when(admin.getQueueInfo("r")).thenReturn(new QueueInformation("r",0,1));
        when(admin.getQueueInfo("g")).thenReturn(new QueueInformation("g",0,1));
        when(admin.getQueueInfo("d")).thenReturn(new QueueInformation("d",0,0));
        when(admin.getQueueInfo("demo.dlq")).thenReturn(new QueueInformation("demo.dlq",1,0));
        var status=service.status();
        assertEquals(0L,status.quarantined()); assertEquals(1L,status.demoQuarantined()); assertTrue(status.demoEnabled());
    }
}
