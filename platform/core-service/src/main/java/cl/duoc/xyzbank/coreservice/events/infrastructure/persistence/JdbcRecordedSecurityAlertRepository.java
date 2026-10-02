package cl.duoc.xyzbank.coreservice.events.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public class JdbcRecordedSecurityAlertRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRecordedSecurityAlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String eventId, String cardId, LocalDate occurredOn) {
        jdbcTemplate.update(
                """
                INSERT INTO recorded_security_alerts (event_id, card_id, occurred_on)
                VALUES (?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                eventId,
                UUID.fromString(cardId),
                occurredOn);
    }
}
