package cl.duoc.xyzbank.coreservice.events.infrastructure.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnExpression(
        "${interests.kafka.enabled:false} || ${app.events.transaction-confirmed.enabled:false} || ${app.events.security-alerts.enabled:false}")
public class OutboxEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRelay.class);
    private static final String TRANSACTION_CONFIRMED = "TransactionConfirmed";
    private static final String CARD_BLOCKED = "CardBlocked";
    private static final Set<String> INTEREST_CREDIT_RESULTS = Set.of(
            "InterestCreditApplied",
            "InterestCreditRejected",
            "InterestCreditReversed");

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String creditResultsTopic;
    private final String transactionsConfirmedTopic;
    private final String securityAlertsTopic;

    public OutboxEventRelay(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${interests.kafka.credit-results-topic}") String creditResultsTopic,
            @Value("${app.events.transaction-confirmed.topic}") String transactionsConfirmedTopic,
            @Value("${app.events.security-alerts.topic}") String securityAlertsTopic) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.creditResultsTopic = creditResultsTopic;
        this.transactionsConfirmedTopic = transactionsConfirmedTopic;
        this.securityAlertsTopic = securityAlertsTopic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:1000}")
    public void publishPending() {
        try {
            pendingEvents().forEach(this::publish);
        } catch (Exception exception) {
            log.warn("Outbox relay will retry on the next tick", exception);
        }
    }

    private void publish(PendingOutboxEvent pending) {
        try {
            String payload = objectMapper.writeValueAsString(pending.message());
            kafkaTemplate.send(pending.topic(), pending.messageKey(), payload).get(5, TimeUnit.SECONDS);
            jdbcTemplate.update(
                    "UPDATE outbox_events SET published = TRUE WHERE id = ? AND published = FALSE",
                    pending.id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Outbox event {} stays until the next relay attempt", pending.eventId(), exception);
        } catch (Exception exception) {
            log.warn("Outbox event {} stays until the next relay attempt", pending.eventId(), exception);
        }
    }

    private List<PendingOutboxEvent> pendingEvents() {
        return jdbcTemplate.query(
                """
                SELECT id, event_id, event_type, schema_version, account_id, period,
                       amount, currency, interest_rate, opening_balance, closing_balance,
                       occurred_on, reason, movement_type, card_id
                FROM outbox_events
                WHERE published = FALSE
                ORDER BY occurred_on NULLS LAST, id
                """,
                (row, rowNumber) -> mapPending(row)).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private PendingOutboxEvent mapPending(java.sql.ResultSet row) throws java.sql.SQLException {
        String eventType = row.getString("event_type");
        String eventId = row.getString("event_id");
        String accountId = row.getString("account_id");
        UUID id = row.getObject("id", UUID.class);
        if (CARD_BLOCKED.equals(eventType)) {
            String cardId = row.getString("card_id");
            if (cardId == null) {
                log.warn("Skipping CardBlocked outbox event {} because it has no card id", eventId);
                return null;
            }
            return new PendingOutboxEvent(
                    id,
                    eventId,
                    cardId,
                    securityAlertsTopic,
                    new SecurityAlertMessage(
                            eventId,
                            eventType,
                            row.getInt("schema_version"),
                            cardId,
                            row.getObject("occurred_on", LocalDate.class)));
        }
        if (TRANSACTION_CONFIRMED.equals(eventType)) {
            return new PendingOutboxEvent(
                    id,
                    eventId,
                    accountId,
                    transactionsConfirmedTopic,
                    new TransactionConfirmedMessage(
                            eventId,
                            eventType,
                            row.getInt("schema_version"),
                            accountId,
                            row.getString("movement_type"),
                            row.getBigDecimal("amount"),
                            row.getString("currency"),
                            row.getObject("occurred_on", LocalDate.class)));
        }
        if (INTEREST_CREDIT_RESULTS.contains(Objects.requireNonNullElse(eventType, ""))) {
            return new PendingOutboxEvent(
                    id,
                    eventId,
                    accountId,
                    creditResultsTopic,
                    new InterestCreditResultMessage(
                            publishedEventId(eventId, eventType),
                            eventType,
                            row.getInt("schema_version"),
                            accountId,
                            row.getObject("period", Integer.class),
                            row.getBigDecimal("amount"),
                            row.getString("currency"),
                            row.getBigDecimal("interest_rate"),
                            row.getBigDecimal("opening_balance"),
                            row.getBigDecimal("closing_balance"),
                            row.getObject("occurred_on", LocalDate.class),
                            row.getString("reason")));
        }
        log.warn(
                "Skipping unknown outbox event_type={} eventId={} until it is recognized",
                eventType,
                eventId);
        return null;
    }

    private String publishedEventId(String eventId, String eventType) {
        String reversalSuffix = ":reversed";
        if ("InterestCreditReversed".equals(eventType) && eventId.endsWith(reversalSuffix)) {
            return eventId.substring(0, eventId.length() - reversalSuffix.length());
        }
        return eventId;
    }

    private record PendingOutboxEvent(
            UUID id, String eventId, String messageKey, String topic, Object message) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SecurityAlertMessage(
            String eventId,
            String eventType,
            int schemaVersion,
            String cardId,
            LocalDate occurredAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record InterestCreditResultMessage(
            String eventId,
            String eventType,
            int schemaVersion,
            String accountId,
            Integer period,
            BigDecimal amount,
            String currency,
            BigDecimal interestRate,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            LocalDate occurredAt,
            String reason) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record TransactionConfirmedMessage(
            String eventId,
            String eventType,
            int schemaVersion,
            String accountId,
            String type,
            BigDecimal amount,
            String currency,
            LocalDate occurredAt) {
    }
}
