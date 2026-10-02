package cl.duoc.xyzbank.coreservice.payments.application.dto;

import java.math.BigDecimal;

public record ExternalPaymentResponse(
        String transactionId,
        String accountId,
        String recipient,
        BigDecimal amount,
        String currency,
        String occurredOn,
        BigDecimal newBalance) {
}
