package com.example.AndesStay.service;

import com.example.AndesStay.entity.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BrokerGatewayTests {
    @Test void rabbitRequiresAcknowledgementAndSetsPersistentIdentity() throws Exception {
        RabbitTemplate rabbit=mock(RabbitTemplate.class);
        doAnswer(invocation->{
            assertEquals(exchange().getName(), invocation.getArgument(0));
            assertEquals("reservation.created", invocation.getArgument(1));
            Message message=invocation.getArgument(2);
            CorrelationData correlation=invocation.getArgument(3);
            assertEquals(correlation.getId(),message.getMessageProperties().getMessageId());
            assertEquals(MessageDeliveryMode.PERSISTENT,message.getMessageProperties().getDeliveryMode());
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null)); return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        new BrokerGateway(rabbit,kafka(),1,exchange()).publish(event("RABBITMQ"));
    }
    @Test void rabbitNegativeConfirmationAndReturnedMessageFailDelivery() {
        for(boolean returned:new boolean[]{false,true}) {
            RabbitTemplate rabbit=mock(RabbitTemplate.class);
            doAnswer(invocation->{
                CorrelationData correlation=invocation.getArgument(3);
                if(returned) correlation.setReturned(new ReturnedMessage(new Message(new byte[0]),312,"NO_ROUTE","","missing"));
                correlation.getFuture().complete(new CorrelationData.Confirm(returned,"Rejected")); return null;
            }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
            assertThrows(IllegalStateException.class,()->new BrokerGateway(rabbit,kafka(),1,exchange()).publish(event("RABBITMQ")));
        }
    }
    @Test void absentRabbitConfirmationTimesOut() {
        assertThrows(TimeoutException.class,()->new BrokerGateway(mock(RabbitTemplate.class),kafka(),0,exchange()).publish(event("RABBITMQ")));
    }
    @Test void kafkaFailureCannotBeTreatedAsPublished() {
        var kafka=kafka();
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        assertThrows(ExecutionException.class,()->new BrokerGateway(mock(RabbitTemplate.class),kafka,1,exchange()).publish(event("KAFKA")));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "RESERVATION_CONFIRMED,reservation.confirmed", "RESERVATION_CANCELLED,reservation.cancelled"
    })
    void statusEventsUseTheirOwnRoutingKey(String type, String key) throws Exception {
        var rabbit=mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            assertEquals(exchange().getName(), invocation.getArgument(0));
            assertEquals(key, invocation.getArgument(1));
            ((CorrelationData) invocation.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true,null));
            return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        var event=new OutboxEvent(UUID.randomUUID().toString(),"RABBITMQ","{\"type\":\""+type+"\"}",LocalDateTime.now());
        new BrokerGateway(rabbit,kafka(),1,exchange()).publish(event);
    }
    @Test void unknownEventDoesNotSilentlyUseAnUnroutableKey() {
        var rabbit=mock(RabbitTemplate.class);
        var event=new OutboxEvent(UUID.randomUUID().toString(),"RABBITMQ","{\"type\":\"UNKNOWN\"}",LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> new BrokerGateway(rabbit,kafka(),1,exchange()).publish(event));
        verifyNoInteractions(rabbit);
    }
    private DirectExchange exchange() {
        return new DirectExchange(com.example.AndesStay.config.RabbitMQConfig.EXCHANGE);
    }
    @SuppressWarnings("unchecked") private KafkaTemplate<String,String> kafka(){return mock(KafkaTemplate.class);}
    private OutboxEvent event(String destination) { return new OutboxEvent(UUID.randomUUID().toString(),destination,"{\"type\":\"RESERVATION_CREATED\"}",LocalDateTime.now()); }
}
