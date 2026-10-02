package cl.duoc.xyzbank.interestsservice.interests.integration;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"interests.credit-results", "interests.credit-results.DLT"})
@DisplayName("The interest credit result dead letter")
class InterestCreditResultDeadLetterIT {

    /*
     * Cases:
     * 1. An unreadable credit result is not applied and lands on the dead-letter topic
     * 2. A readable InterestCreditApplied still closes the calculation after retries are configured
     */

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
        registry.add("spring.kafka.consumer.group-id", () -> "interests-service-credit-results-dlt-it");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("interests.kafka.enabled", () -> "true");
        registry.add("interests.kafka.calculated-topic", () -> "interests.calculated");
        registry.add("interests.kafka.credit-results-topic", () -> "interests.credit-results");
        registry.add("interests.kafka.listener-retry-interval-ms", () -> "20");
        registry.add("interests.kafka.listener-max-retries", () -> "3");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.loadbalancer.enabled", () -> "false");
    }

    @Autowired
    private InterestCalculationRepository calculations;

    @Value("${spring.embedded.kafka.brokers}")
    private String bootstrapServers;

    @Test
    @DisplayName("moves an unreadable credit result to the dead-letter topic")
    void movesAnUnreadableCreditResultToTheDeadLetterTopic() throws Exception {
        String accountId = "account-unreadable";
        String payload = "{not-json";

        publish("interests.credit-results", accountId, payload);

        assertEquals(payload, awaitDeadLetter(accountId));
    }

    @Test
    @DisplayName("closes a readable credit result as applied")
    void closesAReadableCreditResultAsApplied() throws Exception {
        String eventId = "interest:account-dlt-applied:2025";
        calculations.save(InterestCalculation.pending(
                eventId, "account-dlt-applied", 2025, new BigDecimal("35.00"), "USD"));

        publish("interests.credit-results", "account-dlt-applied", """
                {
                  "eventId": "%s",
                  "eventType": "InterestCreditApplied",
                  "schemaVersion": 1,
                  "accountId": "account-dlt-applied",
                  "period": 2025,
                  "amount": "35.00",
                  "currency": "USD",
                  "occurredAt": "2026-01-15"
                }
                """.formatted(eventId));

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            InterestCalculation calculation = calculations.findByEventId(eventId).orElseThrow();
            assertEquals(InterestCalculationStatus.APPLIED, calculation.status());
            assertNull(calculation.reason());
        });
    }

    private void publish(String topic, String accountId, String payload) throws Exception {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(topic, accountId, payload)).get();
        }
    }

    private String awaitDeadLetter(String accountId) {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "interest-credit-results-dlt-" + accountId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of("interests.credit-results.DLT"));
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
}
