package com.example.AndesStayRabbitAdmin.client;

import com.example.AndesStayRabbitAdmin.service.RabbitAdminException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class RabbitManagementClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrokerQueue(String name, boolean durable, @JsonProperty("auto_delete") boolean autoDelete,
            @JsonProperty("messages_ready") Long messagesReady,
            @JsonProperty("messages_unacknowledged") Long messagesUnacknowledged, Integer consumers,
            @JsonProperty("effective_policy_definition") Map<String, Object> effectivePolicy) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrokerExchange(String name, String type, boolean durable) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrokerBinding(String source, String destination, @JsonProperty("routing_key") String routingKey,
            @JsonProperty("properties_key") String propertiesKey, Map<String, Object> arguments) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrokerPolicy(String name, Map<String, Object> definition) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrokerPage<T>(List<T> items, int page, @JsonProperty("page_size") int pageSize,
            @JsonProperty("total_count") long total) {}

    private final RestClient rest;
    private final String baseUrl;
    private final String vhost;

    public RabbitManagementClient(RestClient rest, String baseUrl, String vhost) {
        URI base = URI.create(baseUrl);
        if (!List.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
            throw new IllegalArgumentException("Management URL debe ser una URL base HTTP(S)");
        }
        this.rest = rest;
        this.baseUrl = baseUrl;
        this.vhost = vhost;
    }

    private UriComponentsBuilder address(String resource, String... suffix) {
        return UriComponentsBuilder.fromUriString(baseUrl).pathSegment("api", resource, vhost).pathSegment(suffix);
    }
    private URI uri(String resource, String... suffix) { return address(resource, suffix).build().encode().toUri(); }
    private <T> T request(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status == 404) throw new RabbitAdminException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "El recurso no existe");
            if (status == 400 || status == 409 || status == 412)
                throw new RabbitAdminException(HttpStatus.CONFLICT, "BROKER_CONFLICT", "El recurso está en uso o su definición es incompatible");
            throw unavailable();
        } catch (RestClientException failure) { throw unavailable(); }
    }
    private <T> T required(T value) {
        if (value == null) throw unavailable();
        return value;
    }
    private RabbitAdminException unavailable() {
        return new RabbitAdminException(HttpStatus.SERVICE_UNAVAILABLE, "BROKER_UNAVAILABLE", "RabbitMQ no está disponible para esta operación");
    }

    public BrokerPage<BrokerQueue> queues(int page, int size) {
        URI url = address("queues").queryParam("pagination", true).queryParam("page", page)
                .queryParam("page_size", size).queryParam("sort", "name").build().encode().toUri();
        return request(() -> required(rest.get().uri(url).retrieve()
                .body(new ParameterizedTypeReference<BrokerPage<BrokerQueue>>() {})));
    }
    public BrokerQueue queue(String name) {
        return request(() -> required(rest.get().uri(uri("queues", name)).retrieve().body(BrokerQueue.class)));
    }
    public Optional<BrokerQueue> findQueue(String name) {
        try { return Optional.of(queue(name)); }
        catch (RabbitAdminException failure) { if (failure.status() == HttpStatus.NOT_FOUND) return Optional.empty(); throw failure; }
    }
    public void declareQueue(String name) {
        request(() -> rest.put().uri(uri("queues", name)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("durable", true, "auto_delete", false, "arguments", Map.of("x-queue-type", "classic")))
                .retrieve().toBodilessEntity());
    }
    public void deleteQueue(String name) {
        URI url = address("queues", name).queryParam("if-empty", true).queryParam("if-unused", true).build().encode().toUri();
        request(() -> rest.delete().uri(url).retrieve().toBodilessEntity());
    }
    public BrokerPage<BrokerExchange> exchanges(int page, int size) {
        URI url = address("exchanges").queryParam("pagination", true).queryParam("page", page)
                .queryParam("page_size", size).queryParam("sort", "name").build().encode().toUri();
        return request(() -> required(rest.get().uri(url).retrieve()
                .body(new ParameterizedTypeReference<BrokerPage<BrokerExchange>>() {})));
    }
    public BrokerExchange exchange(String name) {
        return request(() -> required(rest.get().uri(uri("exchanges", name)).retrieve().body(BrokerExchange.class)));
    }
    public Optional<BrokerExchange> findExchange(String name) {
        try { return Optional.of(exchange(name)); }
        catch (RabbitAdminException failure) { if (failure.status() == HttpStatus.NOT_FOUND) return Optional.empty(); throw failure; }
    }
    public void declareExchange(String name, String type) {
        request(() -> rest.put().uri(uri("exchanges", name)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", type, "durable", true, "auto_delete", false, "internal", false, "arguments", Map.of()))
                .retrieve().toBodilessEntity());
    }
    public void deleteExchange(String name) {
        URI url = address("exchanges", name).queryParam("if-unused", true).build().encode().toUri();
        request(() -> rest.delete().uri(url).retrieve().toBodilessEntity());
    }
    public List<BrokerBinding> bindings(String exchange, String queue) {
        return request(() -> required(rest.get().uri(uri("bindings", "e", exchange, "q", queue)).retrieve()
                .body(new ParameterizedTypeReference<List<BrokerBinding>>() {})));
    }
    public void declareBinding(String exchange, String queue, String routingKey) {
        request(() -> rest.post().uri(uri("bindings", "e", exchange, "q", queue)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("routing_key", routingKey, "arguments", Map.of())).retrieve().toBodilessEntity());
    }
    public void deleteBinding(String exchange, String queue, String propertiesKey) {
        request(() -> rest.delete().uri(uri("bindings", "e", exchange, "q", queue, propertiesKey)).retrieve().toBodilessEntity());
    }
    public void putPolicy(String name, String pattern, String applyTo, Map<String, Object> definition) {
        request(() -> rest.put().uri(uri("policies", name)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("pattern", pattern, "apply-to", applyTo, "priority", 10, "definition", definition))
                .retrieve().toBodilessEntity());
    }
    public void deletePolicyIfPresent(String name) {
        try { request(() -> rest.delete().uri(uri("policies", name)).retrieve().toBodilessEntity()); }
        catch (RabbitAdminException failure) { if (failure.status() != HttpStatus.NOT_FOUND) throw failure; }
    }
    public List<BrokerPolicy> policies() {
        return request(() -> required(rest.get().uri(uri("policies")).retrieve()
                .body(new ParameterizedTypeReference<List<BrokerPolicy>>() {})));
    }
}
