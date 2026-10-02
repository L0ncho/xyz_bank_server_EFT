package cl.duoc.xyzbank.coreservice.payments.application.dto;

import java.math.BigDecimal;

public record DepositResponse(
        String transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        String occurredOn,
        BigDecimal newBalance) {
}
