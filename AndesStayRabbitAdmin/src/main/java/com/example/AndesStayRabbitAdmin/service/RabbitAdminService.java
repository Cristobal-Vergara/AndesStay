package com.example.AndesStayRabbitAdmin.service;

import com.example.AndesStayRabbitAdmin.client.RabbitManagementClient;
import com.example.AndesStayRabbitAdmin.client.RabbitManagementClient.*;
import com.example.AndesStayRabbitAdmin.dto.RabbitRequests.*;
import com.example.AndesStayRabbitAdmin.dto.RabbitResponses.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RabbitAdminService {
    private final RabbitManagementClient broker;
    private final RabbitResourceScope scope;

    public RabbitAdminService(RabbitManagementClient broker, RabbitResourceScope scope) {
        this.broker = broker;
        this.scope = scope;
    }

    public Status status() {
        try {
            var queue = broker.queue(scope.reservationQueue());
            return new Status(true, queue.name(), queue.messagesReady(), queue.messagesUnacknowledged(), queue.consumers());
        } catch (RabbitAdminException failure) {
            if (failure.status() == HttpStatus.NOT_FOUND) throw new RabbitAdminException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TOPOLOGY_UNAVAILABLE", "No existe la cola configurada de reservas");
            throw failure;
        }
    }

    public Page<QueueView> queues(int page, int size) {
        var result = broker.queues(page, size);
        return new Page<>(result.items().stream().map(this::queueView).toList(), result.page(), result.pageSize(), result.total());
    }

    public QueueView queue(String name) { return queueView(broker.queue(name)); }

    public QueueView createQueue(QueueCreate request) {
        scope.requireWritable(request.name());
        validateDeadLetterExchange(request.settings());
        if (broker.findQueue(request.name()).isPresent()) throw conflict("La cola ya existe");
        broker.declareQueue(request.name());
        try {
            applyQueuePolicy(request.name(), request.settings());
        } catch (RabbitAdminException failure) {
            // La compensación también exige cola vacía y sin consumidores.
            compensate(() -> {
                broker.deleteQueue(request.name());
                if (broker.findQueue(request.name()).isEmpty()) broker.deletePolicyIfPresent(queuePolicy(request.name()));
            }, failure);
            throw failure;
        }
        return queue(request.name());
    }

    public QueueView updateQueue(String name, QueueSettings settings) {
        scope.requireWritable(name);
        broker.queue(name);
        validateDeadLetterExchange(settings);
        applyQueuePolicy(name, settings);
        return queue(name);
    }

    public void deleteQueue(String name) {
        scope.requireWritable(name);
        broker.queue(name);
        broker.deleteQueue(name);
        if (broker.findQueue(name).isPresent()) throw conflict("La cola sigue en uso");
        broker.deletePolicyIfPresent(queuePolicy(name));
    }

    private void validateDeadLetterExchange(QueueSettings settings) {
        if (settings.deadLetterExchange() != null) {
            scope.requireWritable(settings.deadLetterExchange());
            broker.exchange(settings.deadLetterExchange());
        }
    }

    private void applyQueuePolicy(String name, QueueSettings settings) {
        Map<String, Object> definition = new HashMap<>();
        definition.put("message-ttl", settings.messageTtlMs());
        definition.put("max-length", settings.maxLength());
        if (settings.deadLetterExchange() != null) definition.put("dead-letter-exchange", settings.deadLetterExchange());
        broker.putPolicy(queuePolicy(name), exactName(name), "queues", definition);
    }

    private QueueView queueView(BrokerQueue queue) {
        return new QueueView(queue.name(), queue.durable(), queue.autoDelete(), queue.messagesReady(),
                queue.messagesUnacknowledged(), queue.consumers(),
                queue.effectivePolicy() == null ? Map.of() : queue.effectivePolicy(), scope.writable(queue.name()));
    }

    public Page<ExchangeView> exchanges(int page, int size) {
        var result = broker.exchanges(page, size);
        var policies = managedPolicies();
        return new Page<>(result.items().stream().map(exchange -> exchangeView(exchange, policies)).toList(),
                result.page(), result.pageSize(), result.total());
    }

    public ExchangeView exchange(String name) { return exchangeView(broker.exchange(name), managedPolicies()); }

    public ExchangeView createExchange(ExchangeCreate request) {
        scope.requireWritable(request.name());
        if (broker.findExchange(request.name()).isPresent()) throw conflict("El exchange ya existe");
        broker.declareExchange(request.name(), request.type());
        return exchange(request.name());
    }

    public ExchangeView updateExchange(String name, ExchangeSettings settings) {
        scope.requireWritable(name);
        broker.exchange(name);
        if (settings.alternateExchange() == null) {
            broker.deletePolicyIfPresent(exchangePolicy(name));
        } else {
            scope.requireWritable(settings.alternateExchange());
            if (name.equals(settings.alternateExchange())) throw new RabbitAdminException(
                    HttpStatus.BAD_REQUEST, "INVALID_REFERENCE", "El exchange alternativo debe ser distinto");
            broker.exchange(settings.alternateExchange());
            broker.putPolicy(exchangePolicy(name), exactName(name), "exchanges",
                    Map.of("alternate-exchange", settings.alternateExchange()));
        }
        return exchange(name);
    }

    public void deleteExchange(String name) {
        scope.requireWritable(name);
        broker.exchange(name);
        // Evita dejar referencias operativas de DLX o exchange alternativo sin destino.
        if (managedPolicies().values().stream().anyMatch(policy ->
                name.equals(policy.get("dead-letter-exchange")) || name.equals(policy.get("alternate-exchange")))) {
            throw conflict("El exchange está referenciado por una política operativa");
        }
        broker.deleteExchange(name);
        if (broker.findExchange(name).isPresent()) throw conflict("El exchange sigue en uso");
        broker.deletePolicyIfPresent(exchangePolicy(name));
    }

    private ExchangeView exchangeView(BrokerExchange exchange, Map<String, Map<String, Object>> policies) {
        return new ExchangeView(exchange.name(), exchange.type(), exchange.durable(),
                policies.getOrDefault(exchangePolicy(exchange.name()), Map.of()), scope.writable(exchange.name()));
    }

    private Map<String, Map<String, Object>> managedPolicies() {
        return broker.policies().stream().filter(policy -> policy.name().startsWith(scope.prefix()))
                .collect(Collectors.toMap(BrokerPolicy::name, BrokerPolicy::definition));
    }

    public List<BindingView> bindings(String exchange, String queue) {
        broker.exchange(exchange);
        broker.queue(queue);
        return broker.bindings(exchange, queue).stream().map(this::bindingView).toList();
    }

    public BindingView binding(String exchange, String queue, String routingKey) {
        broker.exchange(exchange);
        broker.queue(queue);
        return bindingView(requireBinding(exchange, queue, routingKey));
    }

    public BindingView createBinding(BindingCreate request) {
        requireBindingScope(request.exchange(), request.queue());
        if (findBinding(request.exchange(), request.queue(), request.routingKey()) != null)
            throw conflict("El binding ya existe");
        broker.declareBinding(request.exchange(), request.queue(), request.routingKey());
        return bindingView(requireBinding(request.exchange(), request.queue(), request.routingKey()));
    }

    public BindingView updateBinding(String exchange, String queue, String oldKey, BindingSettings settings) {
        requireBindingScope(exchange, queue);
        var previous = requireBinding(exchange, queue, oldKey);
        if (oldKey.equals(settings.routingKey())) return bindingView(previous);
        boolean targetExists = findBinding(exchange, queue, settings.routingKey()) != null;
        if (!targetExists) broker.declareBinding(exchange, queue, settings.routingKey());
        try {
            broker.deleteBinding(exchange, queue, previous.propertiesKey());
        } catch (RabbitAdminException failure) {
            if (!targetExists) compensate(() -> {
                var created = requireBinding(exchange, queue, settings.routingKey());
                broker.deleteBinding(exchange, queue, created.propertiesKey());
            }, failure);
            throw failure;
        }
        return bindingView(requireBinding(exchange, queue, settings.routingKey()));
    }

    public void deleteBinding(String exchange, String queue, String routingKey) {
        requireBindingScope(exchange, queue);
        var binding = requireBinding(exchange, queue, routingKey);
        broker.deleteBinding(exchange, queue, binding.propertiesKey());
    }

    private void requireBindingScope(String exchange, String queue) {
        scope.requireWritable(exchange);
        scope.requireWritable(queue);
        broker.exchange(exchange);
        broker.queue(queue);
    }

    private BrokerBinding findBinding(String exchange, String queue, String routingKey) {
        return broker.bindings(exchange, queue).stream()
                .filter(binding -> routingKey.equals(binding.routingKey()) && noArguments(binding)).findFirst().orElse(null);
    }

    private BrokerBinding requireBinding(String exchange, String queue, String routingKey) {
        var binding = findBinding(exchange, queue, routingKey);
        if (binding == null) throw new RabbitAdminException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "El binding no existe");
        return binding;
    }

    private BindingView bindingView(BrokerBinding binding) {
        return new BindingView(binding.source(), binding.destination(), binding.routingKey(), binding.propertiesKey(),
                scope.writable(binding.source()) && scope.writable(binding.destination()) && noArguments(binding));
    }

    private boolean noArguments(BrokerBinding binding) { return binding.arguments() == null || binding.arguments().isEmpty(); }
    private String queuePolicy(String name) { return name + ".queue-policy"; }
    private String exchangePolicy(String name) { return name + ".exchange-policy"; }
    private String exactName(String name) { return "^" + name.replace(".", "\\.") + "$"; }
    private RabbitAdminException conflict(String message) {
        return new RabbitAdminException(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", message);
    }
    private void compensate(Runnable cleanup, RabbitAdminException failure) {
        try { cleanup.run(); } catch (RabbitAdminException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
    }
}
