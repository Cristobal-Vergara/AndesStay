package com.example.andesstay.AndesStayBFF.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BffReservationService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final RestClient client;

    public BffReservationService(@Value("${backend.url:http://localhost:8080}") String backendUrl, RestClient.Builder builder) {
        this.client = builder.clone().baseUrl(backendUrl).build();
    }

    public String getReservations(String token) { return getReservations(token, null, null); }
    public String getReservations(String token, Integer page, Integer size) {
        return get(token, "/api/reservations", pagination(page, size));
    }
    public String getReservation(String token, Long id) {
        return get(token, "/api/reservations/" + id, Map.of());
    }
    public String createReservation(String token, String body) {
        return client.post().uri("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(token)).body(body).retrieve().body(String.class);
    }
    public String updateReservation(String token, Long id, String body) {
        return put(token, "/api/reservations/" + id, body);
    }
    public String updateReservationStatus(String token, Long id, String body) {
        return put(token, "/api/reservations/" + id + "/status", body);
    }
    public String assignOwner(String token, Long id, String body) {
        return put(token, "/api/reservations/" + id + "/owner", body);
    }
    public void deleteReservation(String token, Long id) { delete(token, "/api/reservations/" + id); }

    public String getCatalog(String token) { return getCatalog(token, null, null); }
    public String getCatalog(String token, Integer page, Integer size) {
        return get(token, "/api/catalog/units", pagination(page, size));
    }
    public String getUnit(String token, Long id) { return get(token, "/api/catalog/units/" + id, Map.of()); }
    public String createUnit(String token, String body) {
        return client.post().uri("/api/catalog/units").contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(token)).body(body).retrieve().body(String.class);
    }
    public String updateUnit(String token, Long id, String body) { return put(token, "/api/catalog/units/" + id, body); }
    public void deleteUnit(String token, Long id) { delete(token, "/api/catalog/units/" + id); }

    public String getReports(String token) { return get(token, "/api/reports/kpis", Map.of()); }
    public String getOperations(String token, String date) {
        Map<String, String> filters = new LinkedHashMap<>();
        addFilter(filters, "date", date);
        return get(token, "/api/reports/operations", filters);
    }
    public String getMessagingStatus(String token) {
        return get(token, "/api/messaging/status", Map.of());
    }
    public String publishMessagingDemo(String token) {
        return client.post().uri("/api/messaging/demo").headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(String.class);
    }
    public String getNotifications(String token, Integer page, Integer size) {
        return get(token, "/api/notifications", pagination(page, size));
    }
    public String getAccount(String token) { return get(token, "/api/account", Map.of()); }
    public String getProfile(String token) { return get(token, "/api/account/profile", Map.of()); }
    public boolean hasRegisteredGuestProfile(String token) {
        try {
            String body = getProfile(token);
            if (body == null) return false;
            var profile = JSON.readTree(body);
            if (profile == null || !profile.path("registered").isBoolean() || !profile.path("registered").booleanValue()
                    || !profile.path("roles").isArray()) return false;
            for (var role : profile.path("roles")) {
                if (role.isString() && "Huesped".equals(role.asString())) return true;
            }
            return false;
        } catch (RestClientException | JacksonException error) {
            return false;
        }
    }
    public String registerProfile(String token, String body) {
        return client.post().uri("/api/account/register").contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(token)).body(body).retrieve().body(String.class);
    }

    public String getAudit(String token, String user, String action, String inicio, String fin) {
        return getAudit(token, user, action, inicio, fin, null, null);
    }
    public String getAudit(String token, String user, String action, String inicio, String fin, Integer page, Integer size) {
        Map<String, String> filters = new LinkedHashMap<>();
        addFilter(filters, "user", user); addFilter(filters, "action", action);
        addFilter(filters, "inicio", inicio); addFilter(filters, "fin", fin);
        filters.putAll(pagination(page, size));
        return get(token, "/api/audit", filters);
    }

    private String get(String token, String path, Map<String, String> filters) {
        return client.get().uri(builder -> {
            builder.path(path);
            filters.forEach((name, value) -> builder.queryParam(name, "{" + name + "}"));
            return builder.build(filters);
        }).headers(headers -> headers.setBearerAuth(token)).retrieve().body(String.class);
    }
    private String put(String token, String path, String body) {
        return client.put().uri(path).contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(token)).body(body).retrieve().body(String.class);
    }
    private void delete(String token, String path) {
        client.delete().uri(path).headers(headers -> headers.setBearerAuth(token)).retrieve().toBodilessEntity();
    }
    private Map<String, String> pagination(Integer page, Integer size) {
        Map<String, String> values = new LinkedHashMap<>();
        if (page != null) values.put("page", page.toString());
        if (size != null) values.put("size", size.toString());
        return values;
    }
    private void addFilter(Map<String, String> filters, String name, String value) {
        if (value != null && !value.isBlank()) filters.put(name, value.trim());
    }
}
