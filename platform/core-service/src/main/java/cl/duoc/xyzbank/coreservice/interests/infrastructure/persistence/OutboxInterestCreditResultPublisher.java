package cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence;

import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditRejected;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditReversed;
import cl.duoc.xyzbank.coreservice.interests.application.ports.InterestCreditResultPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class OutboxInterestCreditResultPublisher implements InterestCreditResultPublisher {

    private final JdbcTemplate jdbcTemplate;

    public OutboxInterestCreditResultPublisher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void reject(InterestCreditRejected rejection) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id, event_id, event_type, schema_version, account_id, period, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                rejection.eventId(),
                "InterestCreditRejected",
                1,
                UUID.fromString(rejection.accountId()),
                periodOf(rejection.eventId()),
                rejection.reason());
    }

    @Override
    @Transactional
    public void reverse(InterestCreditReversed reversal) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id, event_id, event_type, schema_version, account_id, period,
                    amount, currency, occurred_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                reversal.eventId(),
                "InterestCreditReversed",
                1,
                UUID.fromString(reversal.accountId()),
                reversal.year(),
                reversal.amount(),
                reversal.currency(),
                java.time.LocalDate.now());
    }

    private int periodOf(String eventId) {
        int separator = eventId.lastIndexOf(':');
        return Integer.parseInt(eventId.substring(separator + 1));
    }
}
