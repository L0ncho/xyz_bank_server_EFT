package cl.duoc.xyzbank.coreservice.interests.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("The interest calculated dead letter")
class InterestCalculatedDeadLetterIT extends AbstractKafkaPostgresIT {

    /*
     * Cases:
     * 1. An unreadable InterestCalculated payload is not credited and lands on the dead-letter topic
     * 2. After that failure is exhausted, a readable event still credits the account
     */

    @DynamicPropertySource
    static void shortListenerRetries(DynamicPropertyRegistry registry) {
        registry.add("interests.kafka.listener-retry-interval-ms", () -> "20");
        registry.add("interests.kafka.listener-max-retries", () -> "3");
        registry.add("spring.kafka.consumer.group-id", () -> "interest-calculated-dlt-it");
        registry.add("interests.kafka.calculated-topic", () -> "interests.calculated.dlt-it");
        registry.add("resilience4j.circuitbreaker.instances.interestEventProcessing.minimumNumberOfCalls", () -> "100");
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("moves an unreadable interest event to the dead-letter topic without crediting")
    void movesAnUnreadableInterestEventToTheDeadLetterTopicWithoutCrediting() throws Exception {
        String accountId = Id.generate().getValue();
        String payload = "{not-json";

        publish("interests.calculated.dlt-it", accountId, payload);

        String deadLetter = awaitDeadLetter(accountId);
        assertEquals(payload, deadLetter);
        assertEquals(0, countOutboxForAccount(accountId));
    }

    @Test
    @DisplayName("credits a readable interest event after a dead-lettered payload")
    void creditsAReadableInterestEventAfterADeadLetteredPayload() throws Exception {
        String poisonAccountId = Id.generate().getValue();
        publish("interests.calculated.dlt-it", poisonAccountId, "{not-json");
        awaitDeadLetter(poisonAccountId);

        Account account = aSavedAccount("9080706099");
        String accountId = account.getId().getValue();
        String eventId = "interest:" + accountId + ":2025";
        publish("interests.calculated.dlt-it", accountId, anInterestCalculated(accountId, eventId));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                assertEquals(
                        new BigDecimal("1035.00"),
                        accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount()));
    }

    private void publish(String topic, String accountId, String payload) throws Exception {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(topic, accountId, payload)).get();
        }
    }

    private String awaitDeadLetter(String accountId) {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "interest-calculated-dlt-" + accountId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of("interests.calculated.dlt-it.DLT"));
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(received::add);
                return received.stream().anyMatch(record -> accountId.equals(record.key()));
            });
        }
        return received.stream()
                .filter(record -> accountId.equals(record.key()))
                .map(ConsumerRecord::value)
                .findFirst()
                .orElseThrow();
    }

    private int countOutboxForAccount(String accountId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE account_id = ?",
                Integer.class,
                UUID.fromString(accountId));
        return count == null ? 0 : count;
    }

    private String anInterestCalculated(String accountId, String eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "InterestCalculated",
                  "schemaVersion": 1,
                  "accountId": "%s",
                  "period": 2025,
                  "amount": "35.00",
                  "currency": "USD",
                  "interestRate": "0.0350",
                  "openingBalance": "1000.00",
                  "closingBalance": "1035.00",
                  "occurredAt": "2026-01-15"
                }
                """.formatted(eventId, accountId);
    }

    private Account aSavedAccount(String accountNumber) {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Interest Customer", customerId.getValue() + "@xyzbank.cl"));
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(accountNumber),
                customerId,
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(account);
        return accountRepository.findById(account.getId()).orElseThrow();
    }
}
