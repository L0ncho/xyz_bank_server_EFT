package cl.duoc.xyzbank.bffatm.withdrawal.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WithdrawalRequest(@NotNull BigDecimal amount, @NotBlank String currency) {
}
