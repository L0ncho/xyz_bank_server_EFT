package cl.duoc.xyzbank.coreservice.payments.application.dto;

import java.math.BigDecimal;

public record ExternalPaymentRequest(
        String accountId,
        String recipient,
        BigDecimal amount,
        String currency,
        String idempotencyKey) {
}
