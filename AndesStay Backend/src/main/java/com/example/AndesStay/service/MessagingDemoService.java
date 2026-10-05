package com.example.AndesStay.service;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MessagingDemoService {
    private final RabbitTemplate rabbit;
    private final String sourceQueue;
    private final String deadQueue;
    public MessagingDemoService(RabbitTemplate rabbit, String sourceQueue, String deadQueue) {
        this.rabbit=rabbit; this.sourceQueue=sourceQueue; this.deadQueue=deadQueue;
    }
    public String deadQueueName() { return deadQueue; }

    public String publish() throws Exception {
        String id=UUID.randomUUID().toString();
        var properties=new MessageProperties();
        properties.setMessageId(id);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        var correlation=new CorrelationData(id);
        rabbit.send("", sourceQueue, new Message(
            ("{\"id\":\""+id+"\",\"type\":\"DELIVERY_DEMO\"}").getBytes(StandardCharsets.UTF_8), properties), correlation);
        if(!correlation.getFuture().get(10,TimeUnit.SECONDS).ack() || correlation.getReturned()!=null)
            throw new IllegalStateException("El broker no confirmó la prueba");
        return id;
    }

    @RabbitListener(queues="#{demoQueue.name}")
    public void receive(Message message) {
        // Fallo determinista, exclusivamente en la cola de demostración.
        throw new IllegalStateException("Prueba controlada de entrega");
    }
}
