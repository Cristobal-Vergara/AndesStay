package com.example.AndesStay.service;

import com.example.AndesStay.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class OutboxDispatchService {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchService.class);
    private final OutboxRepository repository;
    private final BrokerGateway gateway;
    private final Clock clock;

    public OutboxDispatchService(OutboxRepository repository, BrokerGateway gateway, Clock clock) {
        this.repository = repository; this.gateway = gateway; this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void dispatch(String id) {
        var optional = repository.findByIdForUpdate(id);
        if (optional.isEmpty()) return;
        var event = optional.get();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (event.getPublishedAt() != null || event.getNextAttemptAt().isAfter(now)) return;
        try {
            gateway.publish(event);
            event.published(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            long delaySeconds = Math.min(300L, 1L << Math.min(event.getAttempts() + 1, 9));
            event.retryAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusSeconds(delaySeconds));
            log.warn("Evento {} pendiente en {} después de {} intentos ({})",
                    id, event.getDestination(), event.getAttempts(), exception.getClass().getSimpleName());
        }
    }
}
