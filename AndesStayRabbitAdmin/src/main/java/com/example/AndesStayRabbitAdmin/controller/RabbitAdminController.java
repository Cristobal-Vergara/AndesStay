package com.example.AndesStayRabbitAdmin.controller;

import com.example.AndesStayRabbitAdmin.dto.RabbitRequests.*;
import com.example.AndesStayRabbitAdmin.dto.RabbitResponses.*;
import com.example.AndesStayRabbitAdmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static com.example.AndesStayRabbitAdmin.dto.RabbitRequests.NAME;
import static com.example.AndesStayRabbitAdmin.dto.RabbitRequests.ROUTING_KEY;

@RestController
@RequestMapping({"/api/v1/rabbitmq", "/api/rabbitmq"})
public class RabbitAdminController {
    private final RabbitAdminService service;

    public RabbitAdminController(RabbitAdminService service) { this.service = service; }

    @GetMapping("/status")
    public Status status() { return service.status(); }

    @GetMapping("/queues")
    public Page<QueueView> queues(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize) {
        return service.queues(page, pageSize);
    }

    @GetMapping({"/queues/{name}", "/queue/{name}"})
    public QueueView queue(@PathVariable @Pattern(regexp = NAME) String name) { return service.queue(name); }

    @PostMapping("/queues")
    public ResponseEntity<QueueView> createQueue(@Valid @RequestBody QueueCreate request) {
        return ResponseEntity.created(URI.create("/api/v1/rabbitmq/queues/" + request.name())).body(service.createQueue(request));
    }

    @PutMapping("/queues/{name}")
    public QueueView updateQueue(@PathVariable @Pattern(regexp = NAME) String name,
            @Valid @RequestBody QueueSettings settings) { return service.updateQueue(name, settings); }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> deleteQueue(@PathVariable @Pattern(regexp = NAME) String name) {
        service.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exchanges")
    public Page<ExchangeView> exchanges(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize) {
        return service.exchanges(page, pageSize);
    }

    @GetMapping("/exchanges/{name}")
    public ExchangeView exchange(@PathVariable @Pattern(regexp = NAME) String name) { return service.exchange(name); }

    @PostMapping("/exchanges")
    public ResponseEntity<ExchangeView> createExchange(@Valid @RequestBody ExchangeCreate request) {
        return ResponseEntity.created(URI.create("/api/v1/rabbitmq/exchanges/" + request.name())).body(service.createExchange(request));
    }

    @PutMapping("/exchanges/{name}")
    public ExchangeView updateExchange(@PathVariable @Pattern(regexp = NAME) String name,
            @Valid @RequestBody ExchangeSettings settings) { return service.updateExchange(name, settings); }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> deleteExchange(@PathVariable @Pattern(regexp = NAME) String name) {
        service.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bindings")
    public List<BindingView> bindings(@RequestParam @Pattern(regexp = NAME) String exchange,
            @RequestParam @Pattern(regexp = NAME) String queue) { return service.bindings(exchange, queue); }

    @GetMapping("/bindings/{exchange}/{queue}")
    public BindingView binding(@PathVariable @Pattern(regexp = NAME) String exchange,
            @PathVariable @Pattern(regexp = NAME) String queue,
            @RequestParam @Pattern(regexp = ROUTING_KEY) String routingKey) { return service.binding(exchange, queue, routingKey); }

    @PostMapping("/bindings")
    public ResponseEntity<BindingView> createBinding(@Valid @RequestBody BindingCreate request) {
        var location = URI.create("/api/v1/rabbitmq/bindings/" + request.exchange() + "/" + request.queue()
                + "?routingKey=" + URLEncoder.encode(request.routingKey(), StandardCharsets.UTF_8));
        return ResponseEntity.created(location).body(service.createBinding(request));
    }

    @PutMapping("/bindings/{exchange}/{queue}")
    public BindingView updateBinding(@PathVariable @Pattern(regexp = NAME) String exchange,
            @PathVariable @Pattern(regexp = NAME) String queue,
            @RequestParam @Pattern(regexp = ROUTING_KEY) String routingKey,
            @Valid @RequestBody BindingSettings settings) {
        return service.updateBinding(exchange, queue, routingKey, settings);
    }

    @DeleteMapping("/bindings/{exchange}/{queue}")
    public ResponseEntity<Void> deleteBinding(@PathVariable @Pattern(regexp = NAME) String exchange,
            @PathVariable @Pattern(regexp = NAME) String queue,
            @RequestParam @Pattern(regexp = ROUTING_KEY) String routingKey) {
        service.deleteBinding(exchange, queue, routingKey);
        return ResponseEntity.noContent().build();
    }
}
