package cl.duoc.xyzbank.coreservice.payments.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.payments.application.dto.DepositRequest;
import cl.duoc.xyzbank.coreservice.payments.application.dto.DepositResponse;
import cl.duoc.xyzbank.coreservice.payments.application.dto.ExternalPaymentRequest;
import cl.duoc.xyzbank.coreservice.payments.application.dto.ExternalPaymentResponse;
import cl.duoc.xyzbank.coreservice.payments.application.dto.TransferRequest;
import cl.duoc.xyzbank.coreservice.payments.application.dto.TransferResponse;
import cl.duoc.xyzbank.coreservice.payments.application.usecases.DepositAccountUseCase;
import cl.duoc.xyzbank.coreservice.payments.application.usecases.PayExternalRecipientUseCase;
import cl.duoc.xyzbank.coreservice.payments.application.usecases.TransferBetweenAccountsUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
public class PaymentsController {

    private final DepositAccountUseCase depositAccountUseCase;
    private final TransferBetweenAccountsUseCase transferBetweenAccountsUseCase;
    private final PayExternalRecipientUseCase payExternalRecipientUseCase;

    public PaymentsController(
            DepositAccountUseCase depositAccountUseCase,
            TransferBetweenAccountsUseCase transferBetweenAccountsUseCase,
            PayExternalRecipientUseCase payExternalRecipientUseCase) {
        this.depositAccountUseCase = depositAccountUseCase;
        this.transferBetweenAccountsUseCase = transferBetweenAccountsUseCase;
        this.payExternalRecipientUseCase = payExternalRecipientUseCase;
    }

    @PostMapping("/internal/accounts/{accountId}/deposits")
    public ResponseEntity<DepositResponse> deposit(
            @PathVariable String accountId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DepositBody body) {
        DepositResponse response = depositAccountUseCase.execute(
                new DepositRequest(accountId, body.amount(), body.currency(), idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/internal/accounts/{accountId}/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @PathVariable String accountId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferBody body) {
        TransferResponse response = transferBetweenAccountsUseCase.execute(new TransferRequest(
                accountId, body.destinationAccountId(), body.amount(), body.currency(), idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/internal/accounts/{accountId}/external-payments")
    public ResponseEntity<ExternalPaymentResponse> payExternalRecipient(
            @PathVariable String accountId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExternalPaymentBody body) {
        ExternalPaymentResponse response = payExternalRecipientUseCase.execute(new ExternalPaymentRequest(
                accountId, body.recipient(), body.amount(), body.currency(), idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public record DepositBody(@NotNull BigDecimal amount, @NotBlank String currency) {
    }

    public record TransferBody(
            @NotBlank String destinationAccountId,
            @NotNull BigDecimal amount,
            @NotBlank String currency) {
    }

    public record ExternalPaymentBody(
            @NotBlank String recipient,
            @NotNull BigDecimal amount,
            @NotBlank String currency) {
    }
}
