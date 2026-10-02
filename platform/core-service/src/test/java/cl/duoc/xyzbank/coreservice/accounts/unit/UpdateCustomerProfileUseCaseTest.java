package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryCustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.CustomerProfileResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.UpdateCustomerProfileRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.UpdateCustomerProfileUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The UpdateCustomerProfile use case")
class UpdateCustomerProfileUseCaseTest {

    /*
     * Cases:
     * 1. Updates the name and email and persists them
     * 2. Throws not found for an unknown customer
     * 3. Rejects an invalid email and leaves the stored profile unchanged
     */

    @Test
    @DisplayName("updates the name and email and persists them")
    void updatesTheNameAndEmailAndPersistsThem() {
        Id id = Id.generate();
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        repository.save(Customer.create(id, "Jane Doe", "jane.doe@xyzbank.cl"));
        UpdateCustomerProfileUseCase useCase = new UpdateCustomerProfileUseCase(repository);

        CustomerProfileResponse response = useCase.execute(
                new UpdateCustomerProfileRequest(id.getValue(), "Ana Soto", "ana.soto@xyzbank.cl"));

        assertEquals(id.getValue(), response.id());
        assertEquals("Ana Soto", response.fullName());
        assertEquals("ana.soto@xyzbank.cl", response.email());
        Customer persisted = repository.findById(id).orElseThrow();
        assertEquals("Ana Soto", persisted.getFullName());
        assertEquals("ana.soto@xyzbank.cl", persisted.getEmail());
    }

    @Test
    @DisplayName("throws not found for an unknown customer")
    void throwsNotFoundForAnUnknownCustomer() {
        UpdateCustomerProfileUseCase useCase = new UpdateCustomerProfileUseCase(new InMemoryCustomerRepository());

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.execute(new UpdateCustomerProfileRequest(
                        Id.generate().getValue(), "Ana Soto", "ana.soto@xyzbank.cl")));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("rejects an invalid email and leaves the stored profile unchanged")
    void rejectsAnInvalidEmailAndLeavesTheStoredProfileUnchanged() {
        Id id = Id.generate();
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        repository.save(Customer.create(id, "Jane Doe", "jane.doe@xyzbank.cl"));
        UpdateCustomerProfileUseCase useCase = new UpdateCustomerProfileUseCase(repository);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.execute(
                        new UpdateCustomerProfileRequest(id.getValue(), "Ana Soto", "not-an-email")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        Customer persisted = repository.findById(id).orElseThrow();
        assertEquals("Jane Doe", persisted.getFullName());
        assertEquals("jane.doe@xyzbank.cl", persisted.getEmail());
    }
}
