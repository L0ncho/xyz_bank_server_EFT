package cl.duoc.xyzbank.coreservice.auth.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationOutcome;
import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationRequest;
import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationResponse;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.VerifyPinUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class PinVerificationController {

    private final VerifyPinUseCase verifyPinUseCase;

    public PinVerificationController(VerifyPinUseCase verifyPinUseCase) {
        this.verifyPinUseCase = verifyPinUseCase;
    }

    @PostMapping("/internal/auth/atm/pin-verifications")
    @Transactional
    public ResponseEntity<?> verify(@Valid @RequestBody PinVerificationRequest request) {
        PinVerificationOutcome outcome = verifyPinUseCase.execute(request.cardNumber(), request.pin());
        return switch (outcome.result()) {
            case SUCCESS -> ResponseEntity.status(HttpStatus.OK).body(new PinVerificationResponse(outcome.customerId()));
            case INCORRECT -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pinFailure(HttpStatus.UNAUTHORIZED));
            case LOCKED -> ResponseEntity.status(HttpStatus.LOCKED).body(pinFailure(HttpStatus.LOCKED));
        };
    }

    public static ProblemDetail pinFailure(HttpStatus status) {
        return ProblemDetail.forStatusAndDetail(status, "PIN verification failed");
    }
}
