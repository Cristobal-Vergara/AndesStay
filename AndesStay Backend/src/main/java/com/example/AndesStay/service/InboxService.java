package com.example.AndesStay.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class InboxService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public InboxService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Transactional
    public boolean process(String destination, String payload, Runnable action) {
        String id = eventId(payload);
        int inserted = jdbc.update(
                "insert ignore into consumed_events (destination,event_id,received_at) values (?,?,?)",
                destination, id, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (inserted == 0) return false;
        action.run();
        return true;
    }

    private String eventId(String payload) {
        try {
            var node = json.readTree(payload);
            var id = node.get("id");
            if (id != null && id.isString()) return UUID.fromString(id.stringValue()).toString();
        } catch (RuntimeException ignored) {
            // Mensajes anteriores al formato con ID se identifican por su contenido.
        }
        try {
            return "legacy-" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
