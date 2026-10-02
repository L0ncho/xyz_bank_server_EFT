package cl.duoc.xyzbank.coreservice.accounts.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.accounts.application.dto.CustomerProfileResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.UpdateCustomerProfileRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.UpdateCustomerProfileUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UpdateCustomerController {

    private final UpdateCustomerProfileUseCase updateCustomerProfileUseCase;

    public UpdateCustomerController(UpdateCustomerProfileUseCase updateCustomerProfileUseCase) {
        this.updateCustomerProfileUseCase = updateCustomerProfileUseCase;
    }

    @PutMapping("/internal/customers/{customerId}")
    public CustomerProfileResponse update(
            @PathVariable String customerId, @Valid @RequestBody UpdateCustomerBody body) {
        return updateCustomerProfileUseCase.execute(
                new UpdateCustomerProfileRequest(customerId, body.fullName(), body.email()));
    }

    public record UpdateCustomerBody(@NotBlank String fullName, @NotBlank String email) {
    }
}
