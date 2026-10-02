package cl.duoc.xyzbank.coreservice.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

public record PinVerificationRequest(@NotBlank String cardNumber, @NotBlank String pin) {
}
