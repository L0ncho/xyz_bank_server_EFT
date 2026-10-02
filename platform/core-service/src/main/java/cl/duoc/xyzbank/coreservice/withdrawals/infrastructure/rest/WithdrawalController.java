package cl.duoc.xyzbank.coreservice.withdrawals.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.withdrawals.application.dto.WithdrawRequest;
import cl.duoc.xyzbank.coreservice.withdrawals.application.dto.WithdrawalResponse;
import cl.duoc.xyzbank.coreservice.withdrawals.application.usecases.WithdrawAccountUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@RestController
public class WithdrawalController {

    private final WithdrawAccountUseCase withdrawAccountUseCase;

    public WithdrawalController(WithdrawAccountUseCase withdrawAccountUseCase) {
        this.withdrawAccountUseCase = withdrawAccountUseCase;
    }

    @PostMapping("/internal/accounts/{accountId}/withdrawals")
    public ResponseEntity<WithdrawalResponse> withdraw(
            @PathVariable String accountId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WithdrawBody body) {
        WithdrawalResponse response = withdrawAccountUseCase.execute(
                new WithdrawRequest(accountId, body.amount(), body.currency(), idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public record WithdrawBody(@NotNull BigDecimal amount, @NotBlank String currency) {
    }
}
