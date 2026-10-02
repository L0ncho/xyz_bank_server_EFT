package cl.duoc.xyzbank.coreservice.events.application.dto;

import java.time.LocalDate;

public record CardBlocked(String eventId, String cardId, LocalDate occurredAt) {
}
