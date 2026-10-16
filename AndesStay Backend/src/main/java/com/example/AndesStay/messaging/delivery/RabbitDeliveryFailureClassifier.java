package com.example.AndesStay.messaging.delivery;

import com.example.AndesStay.service.InvalidNotificationEventException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.NonTransientDataAccessException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class RabbitDeliveryFailureClassifier {
    private RabbitDeliveryFailureClassifier() {}

    public static boolean retryable(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof InvalidNotificationEventException || cause instanceof InterruptedException
                    || cause instanceof NonTransientDataAccessException
                    && !(cause instanceof DataAccessResourceFailureException)) {
                return false;
            }
        }
        // Fallos temporales de acceso y errores no clasificados tienen un presupuesto finito.
        return true;
    }

    public static boolean interrupted(Throwable failure) {
        if (Thread.currentThread().isInterrupted()) return true;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof InterruptedException) return true;
        }
        return false;
    }
}
