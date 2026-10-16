package com.example.AndesStayRabbitAdmin.service;

import java.util.Set;
import org.springframework.http.HttpStatus;

public record RabbitResourceScope(String prefix, String reservationQueue, Set<String> protectedNames) {
    public RabbitResourceScope {
        if (prefix == null || !prefix.matches("[a-z][a-z0-9-]*(\\.[a-z0-9-]+)+\\.")) {
            throw new IllegalArgumentException("El prefijo operativo necesita al menos dos segmentos y un punto final");
        }
        protectedNames = Set.copyOf(protectedNames);
    }
    public boolean writable(String name) {
        return name != null && name.startsWith(prefix) && name.length() > prefix.length()
            && !protectedNames.contains(name);
    }
    public void requireWritable(String name) {
        if (!writable(name)) throw new RabbitAdminException(HttpStatus.FORBIDDEN,
                "RESOURCE_PROTECTED", "Solo se modifican recursos operativos con prefijo " + prefix);
    }
}
