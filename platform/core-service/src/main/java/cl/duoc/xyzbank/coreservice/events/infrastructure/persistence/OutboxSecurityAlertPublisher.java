package cl.duoc.xyzbank.coreservice.events.infrastructure.persistence;

import cl.duoc.xyzbank.coreservice.events.application.dto.CardBlocked;
import cl.duoc.xyzbank.coreservice.events.application.ports.SecurityAlertPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.events.security-alerts.enabled", havingValue = "true")
public class OutboxSecurityAlertPublisher implements SecurityAlertPublisher {

    private final JdbcTemplate jdbcTemplate;

    public OutboxSecurityAlertPublisher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void publish(CardBlocked alert) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id, event_id, event_type, schema_version, card_id, occurred_on)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                alert.eventId(),
                "CardBlocked",
                1,
                UUID.fromString(alert.cardId()),
                alert.occurredAt());
    }
}
