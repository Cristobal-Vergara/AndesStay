package com.example.AndesStay.service;

import com.example.AndesStay.entity.OutboxEvent;
import com.example.AndesStay.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxRepository repository;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public OutboxService(OutboxRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String destination, String type, Object data) {
        if (!destination.equals("KAFKA") && !destination.equals("RABBITMQ")) {
            throw new IllegalArgumentException("Destino de evento no admitido");
        }
        String id = UUID.randomUUID().toString();
        String payload = json.writeValueAsString(Map.of("id", id, "type", type,
                "occurredAt", clock.instant().toString(), "data", data));
        repository.save(new OutboxEvent(id, destination, payload,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }
}
