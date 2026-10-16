package com.example.AndesStay.messaging.delivery;

import com.example.AndesStay.config.RabbitDeliveryConfig;
import com.example.AndesStay.service.InvalidNotificationEventException;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.Message;
import org.springframework.dao.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RabbitDeliveryHandlerTests {
    private RabbitDeliveryHandler delivery;
    private Channel channel;
    private Message message;

    @BeforeEach void setup() {
        delivery = new RabbitDeliveryConfig().rabbitDeliveryHandler(2, 0, 2, 10000);
        channel = mock(Channel.class);
        message = new Message(new byte[0]);
        message.getMessageProperties().setDeliveryTag(41);
        message.getMessageProperties().setConsumerQueue("verify.delivery");
    }

    @AfterEach void clearInterruption() { Thread.interrupted(); }

    @Test void successfulOperationFinishesBeforeOnlyItsOwnDeliveryIsAcknowledged() throws IOException {
        var completed = new AtomicInteger();
        doAnswer(invocation -> {
            assertEquals(1, completed.get(), "ACK después del efecto");
            return null;
        }).when(channel).basicAck(41, false);
        delivery.handle(message, channel, completed::incrementAndGet);
        verify(channel).basicAck(41, false);
        verifyNoMoreInteractions(channel);
    }

    static Stream<RuntimeException> permanentFailures() {
        return Stream.of(new InvalidNotificationEventException("Evento inválido"),
                new IllegalStateException(new InvalidNotificationEventException("Evento inválido envuelto")),
                new DataIntegrityViolationException("Restricción incumplida"),
                new InvalidDataAccessResourceUsageException("Consulta inválida"));
    }

    @ParameterizedTest @MethodSource("permanentFailures")
    void permanentFailuresAreRejectedWithoutRepeatingTheOperation(RuntimeException failure) throws IOException {
        var attempts = new AtomicInteger();
        delivery.handle(message, channel, () -> {
            attempts.incrementAndGet();
            throw failure;
        });
        assertEquals(1, attempts.get());
        verify(channel).basicNack(41, false, false);
        verifyNoMoreInteractions(channel);
    }

    static Stream<RuntimeException> temporaryFailures() {
        return Stream.of(new TransientDataAccessResourceException("Recurso temporal"),
                new DataAccessResourceFailureException("Conexión no disponible"),
                new RecoverableDataAccessException("Conexión recuperable"),
                new IllegalStateException("Error no clasificado"));
    }

    @ParameterizedTest @MethodSource("temporaryFailures")
    void temporaryFailuresCanRecoverOnTheThirdAttemptBeforeAck(RuntimeException failure) throws IOException {
        var attempts = new AtomicInteger();
        delivery.handle(message, channel, () -> {
            verifyNoInteractions(channel);
            if (attempts.incrementAndGet() < 3) throw failure;
        });
        assertEquals(3, attempts.get());
        verify(channel).basicAck(41, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void exhaustedTemporaryFailureIsNackedOnceWithoutRequeue() throws IOException {
        var attempts = new AtomicInteger();
        delivery.handle(message, channel, () -> {
            attempts.incrementAndGet();
            throw new TransientDataAccessResourceException("No se recuperó");
        });
        assertEquals(3, attempts.get());
        verify(channel).basicNack(41, false, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void failedAckAbortsTheChannelWithoutRepeatingBusinessOrSendingNack() throws IOException {
        var attempts = new AtomicInteger();
        var failure = new IOException("ACK interrumpido");
        doThrow(failure).when(channel).basicAck(41, false);
        assertSame(failure, assertThrows(IOException.class,
                () -> delivery.handle(message, channel, attempts::incrementAndGet)));
        assertEquals(1, attempts.get());
        verify(channel).basicAck(41, false);
        verify(channel).abort();
        verifyNoMoreInteractions(channel);
    }

    @Test void failedNackAlsoAbortsTheChannelAndPropagatesTheTransportError() throws IOException {
        var attempts = new AtomicInteger();
        var failure = new IOException("NACK interrumpido");
        doThrow(failure).when(channel).basicNack(41, false, false);
        assertSame(failure, assertThrows(IOException.class, () -> delivery.handle(message, channel, () -> {
            attempts.incrementAndGet();
            throw new InvalidNotificationEventException("Inválido");
        })));
        assertEquals(1, attempts.get());
        verify(channel).basicNack(41, false, false);
        verify(channel).abort();
        verifyNoMoreInteractions(channel);
    }

    @Test void interruptionPreservesTheFlagAndRequeuesInsteadOfQuarantiningTheEvent() throws IOException {
        var attempts = new AtomicInteger();
        delivery.handle(message, channel, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException(new InterruptedException("Detención"));
        });
        assertEquals(1, attempts.get());
        assertTrue(Thread.currentThread().isInterrupted());
        verify(channel).basicNack(41, false, true);
        verifyNoMoreInteractions(channel);
    }

    @Test void fatalJvmErrorAbortsTheChannelAndPropagatesWithoutAcknowledgement() throws IOException {
        var failure = new AssertionError("Fallo fatal");
        assertSame(failure, assertThrows(AssertionError.class,
                () -> delivery.handle(message, channel, () -> { throw failure; })));
        verify(channel).abort();
        verifyNoMoreInteractions(channel);
    }
}
