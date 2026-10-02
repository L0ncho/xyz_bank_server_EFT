package cl.duoc.xyzbank.coreservice.accounts.application.dto;

import java.math.BigDecimal;

public record OpenAccountRequest(
        String accountNumber,
        String customerId,
        BigDecimal balance,
        String currency) {
}
