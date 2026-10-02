package cl.duoc.xyzbank.coreservice.payments.application.dto;

import java.math.BigDecimal;

public record TransferRequest(
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        String idempotencyKey) {
}
