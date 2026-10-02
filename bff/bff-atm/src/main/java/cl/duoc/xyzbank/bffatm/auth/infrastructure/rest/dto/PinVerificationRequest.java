package cl.duoc.xyzbank.bffatm.auth.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record PinVerificationRequest(@NotBlank String cardNumber, @NotBlank String pin) {
}
