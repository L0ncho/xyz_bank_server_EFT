package cl.duoc.xyzbank.coreservice.interests.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditResponse;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.ReverseInterestCreditUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
public class InterestCreditReversalController {

    private final ReverseInterestCreditUseCase reverseInterestCreditUseCase;

    public InterestCreditReversalController(ReverseInterestCreditUseCase reverseInterestCreditUseCase) {
        this.reverseInterestCreditUseCase = reverseInterestCreditUseCase;
    }

    @PostMapping("/internal/accounts/{accountId}/interest-credit-reversals")
    public ResponseEntity<InterestCreditResponse> reverse(
            @PathVariable String accountId,
            @Valid @RequestBody ReversalBody body) {
        return ResponseEntity.ok(reverseInterestCreditUseCase.execute(accountId, body.year()));
    }

    public record ReversalBody(@Min(1) int year) {
    }
}
