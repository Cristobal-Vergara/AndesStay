package com.example.AndesStay.service;

import com.example.AndesStay.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(name = "andesstay.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository repository;
    private final OutboxDispatchService dispatcher;
    private final Clock clock;

    public OutboxPublisher(OutboxRepository repository, OutboxDispatchService dispatcher, Clock clock) {
        this.repository = repository; this.dispatcher = dispatcher; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${andesstay.outbox.poll-delay:1000}")
    public void publishPending() {
        for (String id : repository.findReadyIds(
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), PageRequest.of(0, 50))) {
            try { dispatcher.dispatch(id); }
            catch (RuntimeException exception) {
                log.warn("No se pudo procesar el evento {} ({})", id, exception.getClass().getSimpleName());
            }
            if (Thread.currentThread().isInterrupted()) break;
        }
    }
}
