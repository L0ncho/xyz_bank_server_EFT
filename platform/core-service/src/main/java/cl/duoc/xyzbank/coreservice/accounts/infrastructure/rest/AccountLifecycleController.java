package cl.duoc.xyzbank.coreservice.accounts.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.MaintainAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.OpenAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.CloseAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.MaintainAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.OpenAccountUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
public class AccountLifecycleController {

    private final OpenAccountUseCase openAccountUseCase;
    private final CloseAccountUseCase closeAccountUseCase;
    private final MaintainAccountUseCase maintainAccountUseCase;

    public AccountLifecycleController(
            OpenAccountUseCase openAccountUseCase,
            CloseAccountUseCase closeAccountUseCase,
            MaintainAccountUseCase maintainAccountUseCase) {
        this.openAccountUseCase = openAccountUseCase;
        this.closeAccountUseCase = closeAccountUseCase;
        this.maintainAccountUseCase = maintainAccountUseCase;
    }

    @PostMapping("/internal/accounts")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountBody body) {
        AccountResponse response = openAccountUseCase.execute(new OpenAccountRequest(
                body.accountNumber(), body.customerId(), body.balance(), body.currency()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/internal/accounts/{accountId}/closures")
    public AccountResponse close(@PathVariable String accountId) {
        return closeAccountUseCase.execute(accountId);
    }

    @PutMapping("/internal/accounts/{accountId}")
    public AccountResponse maintain(
            @PathVariable String accountId, @Valid @RequestBody MaintainAccountBody body) {
        return maintainAccountUseCase.execute(new MaintainAccountRequest(accountId, body.accountNumber()));
    }

    public record OpenAccountBody(
            @NotBlank String accountNumber,
            @NotBlank String customerId,
            @NotNull BigDecimal balance,
            @NotBlank String currency) {
    }

    public record MaintainAccountBody(@NotBlank String accountNumber) {
    }
}
