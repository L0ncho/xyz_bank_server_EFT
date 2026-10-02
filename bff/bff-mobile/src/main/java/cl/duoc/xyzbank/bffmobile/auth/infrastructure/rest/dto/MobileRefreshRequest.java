package cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record MobileRefreshRequest(@NotBlank String deviceId, @NotBlank String refreshToken) {
}
