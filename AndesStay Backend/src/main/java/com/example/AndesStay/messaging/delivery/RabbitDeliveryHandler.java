package com.example.AndesStay.messaging.delivery;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;

import java.io.IOException;

public class RabbitDeliveryHandler {
    private static final Logger log = LoggerFactory.getLogger(RabbitDeliveryHandler.class);
    private final RetryTemplate retry;

    public RabbitDeliveryHandler(RetryTemplate retry) {
        this.retry = retry;
    }

    public void handle(Message message, Channel channel, Runnable action) throws IOException {
        boolean acknowledge = true;
        boolean requeue = false;
        try {
            // La operación transaccional termina (incluido el commit) antes del ACK.
            retry.execute(() -> {
                action.run();
                return null;
            });
        } catch (RetryException failure) {
            if (failure.getLastException() instanceof Error fatal) {
                abort(channel, fatal);
                throw fatal;
            }
            acknowledge = false;
            requeue = RabbitDeliveryFailureClassifier.interrupted(failure);
            if (requeue) Thread.currentThread().interrupt();
            log.warn("Entrega fallida: cola={}, reencolar={}, reintentos={}, causa={}",
                    message.getMessageProperties().getConsumerQueue(), requeue, failure.getRetryCount(),
                    failure.getCause() == null ? "desconocida" : failure.getCause().getClass().getSimpleName());
        }

        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            if (acknowledge) channel.basicAck(tag, false);
            else channel.basicNack(tag, false, requeue);
        } catch (IOException | RuntimeException failure) {
            // No reintentar el negocio ni rechazar un efecto ya guardado si falla el canal.
            // Cerrar este canal permite al broker recuperar las entregas sin confirmar.
            abort(channel, failure);
            throw failure;
        }
    }

    private void abort(Channel channel, Throwable failure) {
        try { channel.abort(); }
        catch (IOException | RuntimeException closingFailure) { failure.addSuppressed(closingFailure); }
    }
}
