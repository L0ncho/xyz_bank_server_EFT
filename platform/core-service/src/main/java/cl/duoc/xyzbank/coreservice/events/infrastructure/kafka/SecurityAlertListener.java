package cl.duoc.xyzbank.coreservice.events.infrastructure.kafka;

import cl.duoc.xyzbank.coreservice.events.infrastructure.persistence.JdbcRecordedSecurityAlertRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "app.events.security-alerts.enabled", havingValue = "true")
public class SecurityAlertListener {

    private final ObjectMapper objectMapper;
    private final JdbcRecordedSecurityAlertRepository recordedSecurityAlerts;

    public SecurityAlertListener(
            ObjectMapper objectMapper, JdbcRecordedSecurityAlertRepository recordedSecurityAlerts) {
        this.objectMapper = objectMapper;
        this.recordedSecurityAlerts = recordedSecurityAlerts;
    }

    @KafkaListener(
            topics = "${app.events.security-alerts.topic}",
            groupId = "core-service-security-alerts",
            autoStartup = "true")
    public void onSecurityAlert(String payload) throws IOException {
        SecurityAlertMessage message = objectMapper.readValue(payload, SecurityAlertMessage.class);
        recordedSecurityAlerts.record(message.eventId(), message.cardId(), message.occurredAt());
    }

    private record SecurityAlertMessage(
            String eventId, String eventType, int schemaVersion, String cardId, LocalDate occurredAt) {
    }
}
