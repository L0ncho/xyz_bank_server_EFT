package cl.duoc.xyzbank.bffatm.withdrawal.infrastructure.rest;

import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalRequest;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalResponse;
import cl.duoc.xyzbank.bffatm.withdrawal.application.usecases.WithdrawalUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class WithdrawalController {

    private final WithdrawalUseCase withdrawalUseCase;

    public WithdrawalController(WithdrawalUseCase withdrawalUseCase) {
        this.withdrawalUseCase = withdrawalUseCase;
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public WithdrawalResponse withdraw(
            @PathVariable String accountId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WithdrawalRequest request) {
        return withdrawalUseCase.execute(accountId, request, idempotencyKey);
    }
}
