package com.example.AndesStay.service;

import com.example.AndesStay.dto.ListenerStatusResponse.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.AndesStay.messaging.RabbitListenerIds.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListenerControlServiceTests {
    private RabbitListenerEndpointRegistry registry;
    private SimpleMessageListenerContainer reception;
    private ListenerControlService service;
    private AtomicBoolean running;

    @BeforeEach void setup() {
        registry = mock(RabbitListenerEndpointRegistry.class);
        reception = mock(SimpleMessageListenerContainer.class);
        running = new AtomicBoolean(true);
        when(reception.isRunning()).thenAnswer(invocation -> running.get());
        when(reception.getQueueNames()).thenReturn(new String[]{"temporary.reception"});
        when(registry.getListenerContainer(RECEPTION)).thenReturn(reception);
        service = new ListenerControlService(registry);
    }

    @Test void listsOnlyAvailableAllowedListenersWithTheirActualQueues() {
        when(registry.getListenerContainer("otherApp")).thenReturn(reception);
        var list = service.list();
        assertEquals(1, list.size());
        assertEquals(RECEPTION, list.get(0).id());
        assertEquals(List.of("temporary.reception"), list.get(0).queues());
        assertEquals(State.RUNNING, list.get(0).state());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.pause("otherApp")).getStatusCode());
        verify(reception, never()).stop(any(Runnable.class));
    }

    @Test void disabledDemoCannotBeStarted() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.resume(DEMO)).getStatusCode());
    }

    @Test void pauseRemainsPendingUntilShutdownCallbackAndCannotRaceResume() {
        AtomicReference<Runnable> completed = new AtomicReference<>();
        doAnswer(invocation -> { running.set(false); completed.set(invocation.getArgument(0)); return null; })
                .when(reception).stop(any(Runnable.class));
        assertEquals(State.STOPPING, service.pause(RECEPTION).state());
        assertEquals(State.STOPPING, service.status(RECEPTION).state());
        assertEquals(State.STOPPING, service.pause(RECEPTION).state());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.resume(RECEPTION)).getStatusCode());
        verify(reception, times(1)).stop(any(Runnable.class));
        verify(reception, never()).start();
        completed.get().run();
        assertEquals(State.STOPPED, service.status(RECEPTION).state());
        assertEquals(State.STOPPED, service.pause(RECEPTION).state());
        verify(reception, times(1)).stop(any(Runnable.class));
    }

    @Test void synchronousCompletionReturnsStopped() {
        doAnswer(invocation -> { running.set(false); ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(reception).stop(any(Runnable.class));
        assertEquals(State.STOPPED, service.pause(RECEPTION).state());
    }

    @Test void repeatedResumeDoesNotCreateExtraConsumers() {
        running.set(false);
        doAnswer(invocation -> { running.set(true); return null; }).when(reception).start();
        assertEquals(State.RUNNING, service.resume(RECEPTION).state());
        assertEquals(State.RUNNING, service.resume(RECEPTION).state());
        verify(reception, times(1)).start();
    }

    @Test void failedStopIsReportedAndDoesNotLeaveStaleStoppingState() {
        doThrow(new IllegalStateException("internal detail")).when(reception).stop(any(Runnable.class));
        var error = assertThrows(ResponseStatusException.class, () -> service.pause(RECEPTION));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertFalse(error.getReason().contains("internal detail"));
        assertEquals(State.RUNNING, service.status(RECEPTION).state());
    }

    @Test void failedStartIsNotReportedAsSuccess() {
        running.set(false);
        doThrow(new IllegalStateException("internal detail")).when(reception).start();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> service.resume(RECEPTION)).getStatusCode());
        assertEquals(State.STOPPED, service.status(RECEPTION).state());
    }

    @Test void startingWithoutRunningIsNotReportedAsSuccess() {
        running.set(false);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> service.resume(RECEPTION)).getStatusCode());
    }

    @Test void stoppingOneAudienceDoesNotBlockTheOther() {
        var guest = mock(SimpleMessageListenerContainer.class);
        when(guest.getQueueNames()).thenReturn(new String[]{"temporary.guest"});
        when(guest.isRunning()).thenReturn(true);
        when(registry.getListenerContainer(GUEST)).thenReturn(guest);
        service.pause(RECEPTION);
        assertEquals(State.RUNNING, service.resume(GUEST).state());
        verify(guest, never()).start();
        verify(guest, never()).stop(any(Runnable.class));
    }

    @Test void simultaneousPauseRequestsStopTheContainerOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.pause(RECEPTION));
            var second = executor.submit(() -> service.pause(RECEPTION));
            assertEquals(State.STOPPING, first.get(2, TimeUnit.SECONDS).state());
            assertEquals(State.STOPPING, second.get(2, TimeUnit.SECONDS).state());
            verify(reception, times(1)).stop(any(Runnable.class));
        } finally { executor.shutdownNow(); }
    }
}
