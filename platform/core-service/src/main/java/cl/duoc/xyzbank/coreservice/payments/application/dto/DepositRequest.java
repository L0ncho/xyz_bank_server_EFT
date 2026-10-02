package cl.duoc.xyzbank.coreservice.payments.application.dto;

import java.math.BigDecimal;

public record DepositRequest(
        String accountId,
        BigDecimal amount,
        String currency,
        String idempotencyKey) {
}
