package cl.duoc.xyzbank.coreservice.interests.application.dto;

import java.math.BigDecimal;

public record InterestCreditReversed(
        String eventId,
        String accountId,
        int year,
        BigDecimal amount,
        String currency) {
}
