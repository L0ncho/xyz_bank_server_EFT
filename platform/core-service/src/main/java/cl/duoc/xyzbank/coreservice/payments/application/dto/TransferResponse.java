package cl.duoc.xyzbank.coreservice.payments.application.dto;

import java.math.BigDecimal;

public record TransferResponse(
        String transactionId,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        String occurredOn,
        BigDecimal sourceBalance,
        BigDecimal destinationBalance) {
}
