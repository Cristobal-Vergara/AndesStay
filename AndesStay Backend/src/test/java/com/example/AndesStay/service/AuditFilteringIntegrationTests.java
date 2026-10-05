package com.example.AndesStay.service;

import com.example.AndesStay.entity.AuditEvent;
import com.example.AndesStay.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:audit_filters;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
        "logging.level.root=WARN", "debug=false"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditService.class, com.example.AndesStay.config.ClockConfig.class})
class AuditFilteringIntegrationTests {

    @Autowired private AuditService service;
    @Autowired private AuditEventRepository repository;
    @MockitoBean private KafkaProducer kafka;
    private List<AuditEvent> events;

    @BeforeEach
    void setUp() {
        events = repository.saveAllAndFlush(List.of(
                event("alice", "RESERVA_CONFIRMADA", 1),
                event("bob", "RESERVA_CONFIRMADA", 2),
                event("alice", "RESERVA_CREADA", 2),
                event("alice", "RESERVA_CONFIRMADA", 3),
                event("alice", "RESERVA_CONFIRMADA", 4)));
    }

    @ParameterizedTest
    @CsvSource({
            "false,false,false,false,4:3:2:1:0",
            "true,false,false,false,4:3:2:0",
            "false,true,false,false,4:3:1:0",
            "false,false,true,false,4:3:2:1",
            "false,false,false,true,3:2:1:0",
            "true,true,false,false,4:3:0",
            "true,false,true,false,4:3:2",
            "true,false,false,true,3:2:0",
            "false,true,true,false,4:3:1",
            "false,true,false,true,3:1:0",
            "false,false,true,true,3:2:1",
            "true,true,true,false,4:3",
            "true,true,false,true,3:0",
            "true,false,true,true,3:2",
            "false,true,true,true,3:1",
            "true,true,true,true,3"
    })
    void allSixteenFilterCombinationsAreAppliedAndSorted(
            boolean user, boolean action, boolean inicio, boolean fin, String expectedIndexes) {
        List<Long> expected = Arrays.stream(expectedIndexes.split(":"))
                .map(Integer::parseInt).map(i -> events.get(i).getId()).toList();

        List<AuditEvent> result = service.getFilteredEvents(user ? "alice" : null,
                action ? "RESERVA_CONFIRMADA" : null, inicio ? date(2) : null, fin ? date(3) : null);

        assertEquals(expected, result.stream().map(AuditEvent::getId).toList());
        verifyNoInteractions(kafka);
    }

    @Test
    void blankFiltersMeanNoFilterAndSurroundingWhitespaceIsTrimmed() {
        assertEquals(5, service.getFilteredEvents("   ", "", null, null).size());
        assertEquals(List.of(events.get(3).getId()),
                service.getFilteredEvents(" alice ", " RESERVA_CONFIRMADA ", date(2), date(3))
                        .stream().map(AuditEvent::getId).toList());
    }

    @Test
    void equalDateBoundsMatchTheExactInstantInclusively() {
        assertEquals(List.of(events.get(2).getId(), events.get(1).getId()),
                service.getFilteredEvents(null, null, date(2), date(2))
                        .stream().map(AuditEvent::getId).toList());
    }

    @Test
    void reversedDateRangeReturnsBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getFilteredEvents(null, null, date(3), date(2)));
        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void unknownValuesAndQueryLikeTextMatchNoEvents() {
        assertTrue(service.getFilteredEvents("alice' OR 1=1 --", null, null, null).isEmpty());
        assertTrue(service.getFilteredEvents(null, "DESCONOCIDA", null, null).isEmpty());
    }

    private AuditEvent event(String user, String action, int day) {
        return new AuditEvent(null, action, 1L, "Evento de prueba", date(day), user);
    }

    private LocalDateTime date(int day) {
        return LocalDateTime.of(2026, 10, day, 10, 0);
    }
}
