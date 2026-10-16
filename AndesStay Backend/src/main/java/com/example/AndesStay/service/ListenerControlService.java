package com.example.AndesStay.service;

import com.example.AndesStay.dto.ListenerStatusResponse;
import com.example.AndesStay.dto.ListenerStatusResponse.State;
import com.example.AndesStay.messaging.RabbitListenerIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class ListenerControlService {
    private static final Logger LOG = LoggerFactory.getLogger(ListenerControlService.class);
    private static final List<String> IDS = List.of(RabbitListenerIds.RECEPTION, RabbitListenerIds.GUEST, RabbitListenerIds.DEMO);
    private final RabbitListenerEndpointRegistry registry;
    private final Map<String, Control> controls = Map.of(
            RabbitListenerIds.RECEPTION, new Control("Avisos de recepción"),
            RabbitListenerIds.GUEST, new Control("Avisos de huéspedes"),
            RabbitListenerIds.DEMO, new Control("Prueba local de entrega"));

    public ListenerControlService(RabbitListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    public List<ListenerStatusResponse> list() {
        return IDS.stream().filter(id -> registry.getListenerContainer(id) != null).map(this::status).toList();
    }

    public ListenerStatusResponse status(String id) {
        Control control = control(id);
        synchronized (control) {
            return snapshot(id, control, container(id));
        }
    }

    public ListenerStatusResponse pause(String id) {
        Control control = control(id);
        synchronized (control) {
            MessageListenerContainer container = container(id);
            if (!control.stopping && container.isRunning()) {
                control.stopping = true;
                try {
                    // The completion callback runs after the container has finished its shutdown.
                    container.stop(() -> {
                        synchronized (control) {
                            control.stopping = false;
                            LOG.info("RabbitMQ listener {}: pausa completada", id);
                        }
                    });
                } catch (RuntimeException error) {
                    control.stopping = false;
                    throw unavailable(id, "pausar", error);
                }
            }
            return snapshot(id, control, container);
        }
    }

    public ListenerStatusResponse resume(String id) {
        Control control = control(id);
        synchronized (control) {
            MessageListenerContainer container = container(id);
            if (control.stopping) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El consumidor aún está terminando su pausa. Espera y vuelve a consultar.");
            }
            if (!container.isRunning()) {
                try {
                    container.start();
                } catch (RuntimeException error) {
                    throw unavailable(id, "reanudar", error);
                }
                if (!container.isRunning()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo iniciar el consumidor.");
                }
                LOG.info("RabbitMQ listener {}: inicio solicitado", id);
            }
            return snapshot(id, control, container);
        }
    }

    private ListenerStatusResponse snapshot(String id, Control control, MessageListenerContainer container) {
        List<String> queues = container instanceof AbstractMessageListenerContainer rabbit
                ? List.of(rabbit.getQueueNames()) : List.of();
        State state = control.stopping ? State.STOPPING : container.isRunning() ? State.RUNNING : State.STOPPED;
        return new ListenerStatusResponse(id, control.label, queues, state);
    }

    private Control control(String id) {
        Control control = controls.get(id);
        if (control == null) throw notFound();
        return control;
    }

    private MessageListenerContainer container(String id) {
        MessageListenerContainer container = registry.getListenerContainer(id);
        if (container == null) throw notFound();
        return container;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor no disponible para control.");
    }

    private ResponseStatusException unavailable(String id, String action, RuntimeException error) {
        LOG.warn("No se pudo {} el listener {}", action, id, error);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo " + action + " el consumidor.");
    }

    private static final class Control {
        private final String label;
        private boolean stopping;

        private Control(String label) { this.label = label; }
    }
}
