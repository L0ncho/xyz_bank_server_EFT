package cl.duoc.xyzbank.coreservice.interests.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.interests.application.dto.CreditInterestRequest;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditResponse;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.CreditInterestUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@RestController
public class InterestCreditController {

    private final CreditInterestUseCase creditInterestUseCase;

    public InterestCreditController(CreditInterestUseCase creditInterestUseCase) {
        this.creditInterestUseCase = creditInterestUseCase;
    }

    @PostMapping("/internal/accounts/{accountId}/interest-credits")
    public ResponseEntity<InterestCreditResponse> credit(
            @PathVariable String accountId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InterestCreditBody body) {
        InterestCreditResponse response = creditInterestUseCase.execute(new CreditInterestRequest(
                accountId,
                body.year(),
                body.amount(),
                body.currency(),
                body.interestRate(),
                body.openingBalance(),
                body.closingBalance(),
                idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public record InterestCreditBody(
            @Min(1) int year,
            @NotNull BigDecimal amount,
            @NotBlank String currency,
            @NotNull BigDecimal interestRate,
            @NotNull BigDecimal openingBalance,
            @NotNull BigDecimal closingBalance) {
    }
}
