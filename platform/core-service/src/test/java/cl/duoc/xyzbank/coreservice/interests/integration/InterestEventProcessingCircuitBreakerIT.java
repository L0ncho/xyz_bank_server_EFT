package cl.duoc.xyzbank.coreservice.interests.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Properties;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("The interest event processing circuit breaker")
class InterestEventProcessingCircuitBreakerIT extends AbstractKafkaPostgresIT {

    /*
     * Cases:
     * 1. Repeated unreadable events open interestEventProcessing
     * 2. While it is open, a readable interest event does not credit the account
     */

    @DynamicPropertySource
    static void openQuickly(DynamicPropertyRegistry registry) {
        registry.add("resilience4j.circuitbreaker.instances.interestEventProcessing.minimumNumberOfCalls", () -> "2");
        registry.add("resilience4j.circuitbreaker.instances.interestEventProcessing.slidingWindowSize", () -> "2");
        registry.add("resilience4j.circuitbreaker.instances.interestEventProcessing.failureRateThreshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.interestEventProcessing.waitDurationInOpenState", () -> "30s");
        registry.add("interests.kafka.listener-retry-interval-ms", () -> "10");
        registry.add("interests.kafka.listener-max-retries", () -> "0");
        registry.add("spring.kafka.consumer.group-id", () -> "interest-event-processing-it");
        registry.add("interests.kafka.calculated-topic", () -> "interests.calculated.breaker-it");
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    @DisplayName("does not credit while interest event processing is open")
    void doesNotCreditWhileInterestEventProcessingIsOpen() throws Exception {
        publish("{not-json", Id.generate().getValue());
        publish("{still-not-json", Id.generate().getValue());

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() ->
                circuitBreakerRegistry.circuitBreaker("interestEventProcessing").getState() == CircuitBreaker.State.OPEN);

        Account account = aSavedAccount();
        publish(anInterestCalculated(account.getId().getValue()), account.getId().getValue());

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).until(() -> true);
        assertEquals(
                new BigDecimal("1000.00"),
                accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
    }

    private void publish(String payload, String accountId) throws Exception {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>("interests.calculated.breaker-it", accountId, payload)).get();
        }
    }

    private String anInterestCalculated(String accountId) {
        return """
                {
                  "eventId": "interest:%s:2025",
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
                """.formatted(accountId, accountId);
    }

    private Account aSavedAccount() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Interest Customer", customerId.getValue() + "@xyzbank.cl"));
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(String.valueOf(1000000000L + Math.abs(customerId.getValue().hashCode() % 1000000000L))),
                customerId,
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(account);
        return account;
    }
}
