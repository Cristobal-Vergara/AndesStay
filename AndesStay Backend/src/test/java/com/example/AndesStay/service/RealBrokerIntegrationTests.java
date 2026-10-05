package com.example.AndesStay.service;

import com.example.AndesStay.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_MYSQL",matches="true")
@SpringBootTest(properties={
    "spring.datasource.url=${TEST_FRESH_DB_URL}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.username=${TEST_DB_USERNAME}","spring.datasource.password=${TEST_DB_PASSWORD}",
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.rabbitmq.host=localhost","spring.rabbitmq.port=${TEST_RABBITMQ_PORT:5672}","spring.rabbitmq.username=${RABBITMQ_USERNAME}",
    "spring.rabbitmq.password=${RABBITMQ_PASSWORD}",
    "spring.rabbitmq.listener.simple.auto-startup=true","spring.rabbitmq.dynamic=true",
    "spring.rabbitmq.publisher-confirm-type=correlated","spring.rabbitmq.publisher-returns=true","spring.rabbitmq.template.mandatory=true",
    "spring.kafka.bootstrap-servers=${TEST_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}","spring.kafka.listener.auto-startup=true",
    "spring.kafka.admin.auto-create=true","spring.kafka.consumer.group-id=andesstay-mysql-verification",
    "spring.kafka.consumer.auto-offset-reset=earliest","spring.kafka.consumer.enable-auto-commit=false",
    "spring.kafka.listener.ack-mode=record",
    "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
    "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
    "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
    "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
    "andesstay.outbox.enabled=false", "andesstay.messaging.demo-enabled=true",
    "andesstay.messaging.demo-retention-ms=2000"
})
class RealBrokerIntegrationTests {
    private static final String PREFIX = "andesstay.verify." + UUID.randomUUID().toString().replace("-", "");

    @org.springframework.test.context.DynamicPropertySource
    static void protectExternalDatabase(org.springframework.test.context.DynamicPropertyRegistry registry) {
        String url=System.getenv("TEST_FRESH_DB_URL");
        if(url==null || !url.matches("jdbc:mysql://localhost:[0-9]+/andesstay_test_[a-z0-9_]+\\?.*")) {
            throw new IllegalStateException("Solo se permiten esquemas temporales MySQL locales");
        }
        registry.add("andesstay.rabbitmq.exchange", () -> PREFIX + ".direct");
        registry.add("andesstay.rabbitmq.reservation-queue", () -> PREFIX + ".reception");
        registry.add("andesstay.rabbitmq.guest-queue", () -> PREFIX + ".guest");
        registry.add("andesstay.rabbitmq.dead-letter-exchange", () -> PREFIX + ".dlx");
        registry.add("andesstay.rabbitmq.dead-letter-queue", () -> PREFIX + ".dlq");
        registry.add("andesstay.rabbitmq.demo-queue", () -> PREFIX + ".demo");
        registry.add("andesstay.rabbitmq.demo-dlx", () -> PREFIX + ".demo.dlx");
        registry.add("andesstay.rabbitmq.demo-dlq", () -> PREFIX + ".demo.dlq");
    }
    @Autowired ReservationService service;
    @Autowired UnitRepository units;
    @Autowired OutboxRepository outbox;
    @Autowired OutboxDispatchService dispatch;
    @Autowired InboxService inbox;
    @MockitoSpyBean ReservationNotificationService notices;
    @MockitoSpyBean MessagingDemoService demo;
    @Autowired MessagingStatusService messagingStatus;
    @Autowired JdbcTemplate jdbc;
    @Autowired AmqpAdmin amqp;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @MockitoBean JwtDecoder decoder;

    private final RestClient management=RestClient.builder().requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
        java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build())).defaultHeaders(headers ->
        headers.setBasicAuth(System.getenv("RABBITMQ_USERNAME"),System.getenv("RABBITMQ_PASSWORD"))).build();
    private URI policyUri(String suffix) {
        return URI.create("http://127.0.0.1:"+System.getenv().getOrDefault("TEST_RABBITMQ_MANAGEMENT_PORT","15672")
            +"/api/policies/%2F/"+PREFIX+suffix);
    }
    private void policy(String suffix,String pattern,Map<String,Object> definition) {
        management.put().uri(policyUri(suffix)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("pattern",pattern,"apply-to","queues","priority",20,"definition",definition))
            .retrieve().toBodilessEntity();
    }
    @BeforeEach void configureQuarantine() {
        policy(".source-policy","^"+java.util.regex.Pattern.quote(PREFIX)+"\\.(reception|guest)$",
            Map.of("dead-letter-exchange",PREFIX+".dlx","message-ttl",86400000,"max-length",10000));
        policy(".dead-policy","^"+java.util.regex.Pattern.quote(PREFIX)+ "\\.dlq$", Map.of("message-ttl",604800000));
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        listeners.stop();
        amqp.deleteQueue(PREFIX + ".reception");
        amqp.deleteQueue(PREFIX + ".guest");
        amqp.deleteExchange(PREFIX + ".direct");
        amqp.deleteQueue(PREFIX + ".dlq");
        amqp.deleteExchange(PREFIX + ".dlx");
        amqp.deleteQueue(PREFIX + ".demo");
        amqp.deleteQueue(PREFIX + ".demo.dlq");
        amqp.deleteExchange(PREFIX + ".demo.dlx");
        management.delete().uri(policyUri(".source-policy")).retrieve().toBodilessEntity();
        management.delete().uri(policyUri(".dead-policy")).retrieve().toBodilessEntity();
    }

    @Test void reservationRoutingReachesIndependentRealConsumersAndKeepsGuestPrivacy() throws Exception {
        assertTrue(System.getenv("TEST_FRESH_DB_URL").contains("/andesstay_test_"));
        var unit=units.saveAndFlush(new com.example.AndesStay.entity.Unit(null,"Broker prueba","HABITACION",2,BigDecimal.TEN,true,"Prueba"));
        authenticate("Huesped", WorkflowIntegrationTests.GUEST);
        var reservation=service.createReservation(new com.example.AndesStay.entity.Reservation(null,"Prueba",unit.getId(),
            LocalDate.of(2027,1,1),LocalDate.of(2027,1,2),"CREADA"));
        var created=outbox.findAll().stream().filter(e -> e.getDestination().equals("RABBITMQ")).findFirst().orElseThrow();
        authenticate("Recepcionista", WorkflowIntegrationTests.STAFF);
        service.updateStatus(reservation.getId(), "CONFIRMADA");
        service.updateStatus(reservation.getId(), "CANCELADA");
        var events=outbox.findAll();
        assertEquals(7,events.size());
        for(var event:events) dispatch.dispatch(event.getId());
        assertEquals(7,outbox.findAll().stream().filter(e->e.getPublishedAt()!=null).count());
        Instant deadline=Instant.now().plus(Duration.ofSeconds(30));
        while(Instant.now().isBefore(deadline)) {
            long received=events.stream().filter(event -> received(event.getDestination(),event.getId())==1).count();
            long guestReceived=events.stream().filter(event -> event.getDestination().equals("RABBITMQ")
                && !event.getId().equals(created.getId()) && received("RABBITMQ_GUEST",event.getId())==1).count();
            if(received==7 && guestReceived==2) break;
            Thread.sleep(100);
        }
        for(var event:events) {
            assertEquals(1L,received(event.getDestination(),event.getId()));
            assertFalse(inbox.process(event.getDestination(),event.getPayload(),()->fail("Una entrega repetida no debe crear otro aviso")));
            if(event.getDestination().equals("RABBITMQ") && !event.getId().equals(created.getId())) {
                assertEquals(1L,received("RABBITMQ_GUEST",event.getId()));
                assertFalse(inbox.process("RABBITMQ_GUEST",event.getPayload(),()->fail("Cada consumidor deduplica su propia copia")));
            }
        }
        assertEquals(0L,received("RABBITMQ_GUEST",created.getId()),"La routing key created no está enlazada a la cola del huésped");
        assertEquals(3,notices.list(0,10).getTotalElements());
        authenticate("Huesped", WorkflowIntegrationTests.GUEST);
        assertEquals(2,notices.list(0,10).getTotalElements());
        assertEquals(List.of("CANCELADA","CONFIRMADA"),notices.list(0,10).map(n -> n.status()).getContent());
        authenticate("Huesped", "55555555-5555-5555-5555-555555555555");
        assertEquals(0,notices.list(0,10).getTotalElements());
        var unknown=new CorrelationData(UUID.randomUUID().toString());
        rabbit.send(PREFIX+".direct", "reservation.unbound", new Message(new byte[0]), unknown);
        assertTrue(unknown.getFuture().get(10, java.util.concurrent.TimeUnit.SECONDS).ack());
        assertNotNull(unknown.getReturned(),"Mandatory debe devolver los mensajes sin un binding");
        verifyRetriesAndQuarantine();
        verifyDemoAndRetention();
        verifyExpiryAndOverflow();

    }

    private void verifyRetriesAndQuarantine() throws Exception {
        String poisonId=UUID.randomUUID().toString();
        String poison=payload(poisonId,0);
        rabbit.convertAndSend(PREFIX+".direct","reservation.created",poison);
        await(() -> amqp.getQueueInfo(PREFIX+".dlq").getMessageCount()==1,Duration.ofSeconds(15));
        verify(notices,times(3)).receive(poison,false);
        assertEquals(0,received("RABBITMQ",poisonId),"Cada fallo debe revertir su entrada en inbox");
        Message dead=rabbit.receive(PREFIX+".dlq");
        assertNotNull(dead);
        assertEquals(poison,new String(dead.getBody(),java.nio.charset.StandardCharsets.UTF_8));
        var deaths=dead.getMessageProperties().getXDeathHeader();
        assertEquals("rejected",deaths.get(0).get("reason").toString());
        assertEquals(PREFIX+".reception",deaths.get(0).get("queue").toString());

        String retryId=UUID.randomUUID().toString(), transientPayload=payload(retryId,789);
        var attempts=new AtomicInteger();
        doAnswer(invocation -> {
            if(attempts.incrementAndGet()<3) throw new IllegalStateException("Fallo transitorio controlado");
            return invocation.callRealMethod();
        }).when(notices).receive(transientPayload,false);
        rabbit.convertAndSend(PREFIX+".direct","reservation.created",transientPayload);
        await(() -> received("RABBITMQ",retryId)==1,Duration.ofSeconds(15));
        assertEquals(3,attempts.get());
        assertEquals(0,amqp.getQueueInfo(PREFIX+".dlq").getMessageCount());
        assertFalse(inbox.process("RABBITMQ",transientPayload,()->fail("El reintento no duplica el aviso")));
    }
    private void verifyDemoAndRetention() throws Exception {
        String id=demo.publish();
        await(() -> messagingStatus.status().demoQuarantined()==1,Duration.ofSeconds(15));
        verify(demo,times(3)).receive(any(Message.class));
        assertEquals(0L,messagingStatus.status().quarantined());
        assertEquals(0,received("RABBITMQ",id),"La demo no escribe eventos de reserva");
        await(() -> messagingStatus.status().demoQuarantined()==0,Duration.ofSeconds(10));
    }
    private void verifyExpiryAndOverflow() throws Exception {
        listeners.stop();
        policy(".source-policy","^"+java.util.regex.Pattern.quote(PREFIX)+"\\.(reception|guest)$",
            Map.of("dead-letter-exchange",PREFIX+".dlx","message-ttl",200,"max-length",10));
        rabbit.convertAndSend("",PREFIX+".reception",payload(UUID.randomUUID().toString(),111));
        await(() -> amqp.getQueueInfo(PREFIX+".dlq").getMessageCount()==1,Duration.ofSeconds(10));
        Message expired=rabbit.receive(PREFIX+".dlq");
        assertNotNull(expired);
        assertEquals("expired",expired.getMessageProperties().getXDeathHeader().get(0).get("reason").toString());
        policy(".source-policy","^"+java.util.regex.Pattern.quote(PREFIX)+"\\.(reception|guest)$",
            Map.of("dead-letter-exchange",PREFIX+".dlx","message-ttl",60000,"max-length",1));
        // Dejar que el broker aplique el cambio de política antes de publicar.
        await(() -> {
            var definition=management.get().uri(URI.create(policyUri(".source-policy").toString().replace("/policies/", "/queues/").replace(PREFIX+".source-policy", PREFIX+".reception")))
                .retrieve().body(Map.class);
            return definition!=null && ((Map<?,?>)definition.get("effective_policy_definition")).get("max-length").equals(1);
        },Duration.ofSeconds(5));
        rabbit.convertAndSend("",PREFIX+".reception",payload(UUID.randomUUID().toString(),112));
        rabbit.convertAndSend("",PREFIX+".reception",payload(UUID.randomUUID().toString(),113));
        await(() -> amqp.getQueueInfo(PREFIX+".dlq").getMessageCount()==1,Duration.ofSeconds(10));
        Message overflow=rabbit.receive(PREFIX+".dlq");
        assertNotNull(overflow);
        assertEquals("maxlen",overflow.getMessageProperties().getXDeathHeader().get(0).get("reason").toString());
        policy(".dead-policy","^"+java.util.regex.Pattern.quote(PREFIX)+"\\.dlq$",Map.of("message-ttl",200));
        rabbit.convertAndSend("",PREFIX+".dlq","retention-check");
        await(() -> amqp.getQueueInfo(PREFIX+".dlq").getMessageCount()==1,Duration.ofSeconds(5));
        await(() -> amqp.getQueueInfo(PREFIX+".dlq").getMessageCount()==0,Duration.ofSeconds(10));
    }
    private String payload(String id,long reservationId) {
        return "{\"id\":\""+id+"\",\"type\":\"RESERVATION_CREATED\",\"data\":{\"reservationId\":"+reservationId+",\"unitId\":1}}";
    }
    private void await(BooleanSupplier condition,Duration timeout) throws Exception {
        Instant end=Instant.now().plus(timeout);
        while(Instant.now().isBefore(end)) { if(condition.getAsBoolean()) return; Thread.sleep(100); }
        assertTrue(condition.getAsBoolean(),"El broker no alcanzó el estado esperado");
    }

    private long received(String destination, String id) {
        return jdbc.queryForObject("select count(*) from consumed_events where destination=? and event_id=?",Long.class,destination,id);
    }
    private void authenticate(String role, String oid) {
        WorkflowIntegrationTests.authenticate(role,WorkflowIntegrationTests.TENANT,oid);
    }
}
