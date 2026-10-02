package cl.duoc.xyzbank.coreservice.auth.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationOutcome;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.VerifyPinUseCase;
import cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher;
import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("Security alert when a card is blocked")
class SecurityAlertKafkaIT extends AbstractKafkaPostgresIT {

    private static final String PIN = "1234";
    private static final String WRONG_PIN = "9999";
    private static final String CARD_ID = "aa0bb1cc-22dd-4ee5-8ff6-001122334455";
    private static final String CUSTOMER_ID = "bb0cc1dd-33ee-4ff5-8016-556677889900";
    private static final String EVENT_ID = CARD_ID + ":blocked";

    @DynamicPropertySource
    static void enableSecurityAlerts(DynamicPropertyRegistry registry) {
        registry.add("app.events.security-alerts.enabled", () -> "true");
    }

    @Autowired
    private VerifyPinUseCase verifyPinUseCase;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final PinHasher pinHasher = new PinHasher();
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureLogs() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogs() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logAppender);
    }

    @Test
    @DisplayName("publishes and records a security alert without the pin when the card is blocked")
    void publishesAndRecordsASecurityAlertWithoutThePinWhenTheCardIsBlocked() throws Exception {
        customerRepository.save(Customer.create(Id.create(CUSTOMER_ID), "Alert Customer", "alert@xyzbank.cl"));
        cardRepository.save(Card.create(
                Id.create(CARD_ID),
                Id.create(CUSTOMER_ID),
                pinHasher.hash(PIN),
                2,
                false,
                0L));

        PinVerificationOutcome outcome = verifyPinUseCase.execute(CARD_ID, WRONG_PIN);

        assertEquals(PinVerificationOutcome.Result.LOCKED, outcome.result());
        JsonNode event = awaitSecurityAlert();
        assertEquals("CardBlocked", event.get("eventType").asText());
        assertEquals(EVENT_ID, event.get("eventId").asText());
        assertEquals(CARD_ID, event.get("cardId").asText());
        assertEquals(1, event.get("schemaVersion").asInt());
        assertFalse(event.has("pin"));
        assertPayloadOmitsThePin(event);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(1, countRecordedAlerts()));
        assertTrue(publishedFlag());
        String recorded = jdbcTemplate.queryForObject(
                """
                SELECT event_id || ' ' || card_id::text || ' ' || occurred_on::text
                FROM recorded_security_alerts
                WHERE event_id = ?
                """,
                String.class,
                EVENT_ID);
        assertFalse(recorded.contains(PIN));
        assertFalse(recorded.contains(WRONG_PIN));
        assertLogsOmitThePin();
    }

    private JsonNode awaitSecurityAlert() throws Exception {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = securityAlertConsumer()) {
            consumer.subscribe(List.of("security.alerts"));
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (CARD_ID.equals(record.key()) && EVENT_ID.equals(eventId(record.value()))) {
                        matching.add(record);
                    }
                });
                return !matching.isEmpty();
            });
        }
        return objectMapper.readTree(matching.getFirst().value());
    }

    private String eventId(String payload) {
        try {
            return objectMapper.readTree(payload).get("eventId").asText();
        } catch (Exception exception) {
            return "";
        }
    }

    private KafkaConsumer<String, String> securityAlertConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "security-alert-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private void assertPayloadOmitsThePin(JsonNode event) {
        Iterator<String> fieldNames = event.fieldNames();
        while (fieldNames.hasNext()) {
            String value = event.get(fieldNames.next()).asText();
            assertFalse(value.contains(PIN));
            assertFalse(value.contains(WRONG_PIN));
        }
    }

    private void assertLogsOmitThePin() {
        boolean pinInLogs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message != null && (message.contains(PIN) || message.contains(WRONG_PIN)));
        assertFalse(pinInLogs);
    }

    private boolean publishedFlag() {
        Boolean published = jdbcTemplate.queryForObject(
                "SELECT published FROM outbox_events WHERE event_id = ?",
                Boolean.class,
                EVENT_ID);
        return Boolean.TRUE.equals(published);
    }

    private int countRecordedAlerts() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recorded_security_alerts WHERE event_id = ?",
                Integer.class,
                EVENT_ID);
        return count == null ? 0 : count;
    }
}
