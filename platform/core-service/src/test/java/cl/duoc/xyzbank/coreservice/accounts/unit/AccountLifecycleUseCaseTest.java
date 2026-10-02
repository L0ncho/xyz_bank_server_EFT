package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountStatus;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryCustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.MaintainAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.OpenAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.CloseAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.MaintainAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.OpenAccountUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The account lifecycle use cases")
class AccountLifecycleUseCaseTest {

    /*
     * Cases:
     * 1. Opens an account and persists it
     * 2. Rejects a duplicate account number as a conflict and leaves the seed number in place
     * 3. Rejects an unknown customer
     * 4. Rejects an account number that is not 10 digits
     * 5. Closes an open account
     * 6. Maintains the account number of an open account
     * 7. Rejects maintaining an account onto a number that already exists
     */

    private final InMemoryCustomerRepository customerRepository = new InMemoryCustomerRepository();
    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final OpenAccountUseCase openAccountUseCase =
            new OpenAccountUseCase(customerRepository, accountRepository);
    private final CloseAccountUseCase closeAccountUseCase = new CloseAccountUseCase(accountRepository);
    private final MaintainAccountUseCase maintainAccountUseCase = new MaintainAccountUseCase(accountRepository);

    @Test
    @DisplayName("opens an account and persists it")
    void opensAnAccountAndPersistsIt() {
        Id customerId = savedCustomer();

        AccountResponse opened = openAccountUseCase.execute(
                new OpenAccountRequest("2000000001", customerId.getValue(), new BigDecimal("0.00"), "USD"));

        assertEquals("2000000001", opened.accountNumber());
        assertEquals(customerId.getValue(), opened.customerId());
        assertEquals(new BigDecimal("0.00"), opened.balance());
        assertEquals("USD", opened.currency());
        assertEquals("OPEN", opened.status());
        Account persisted = accountRepository.findById(Id.create(opened.id())).orElseThrow();
        assertEquals(AccountStatus.OPEN, persisted.getStatus());
    }

    @Test
    @DisplayName("rejects a duplicate account number as a conflict and leaves the seed number in place")
    void rejectsADuplicateAccountNumberAsAConflictAndLeavesTheSeedNumberInPlace() {
        Id customerId = savedCustomer();
        Account seed = Account.create(
                Id.create("22222222-2222-2222-2222-222222222222"),
                AccountNumber.create("1000000001"),
                customerId,
                Money.create(new BigDecimal("1500.00"), "USD"));
        accountRepository.save(seed);

        DomainException exception = assertThrows(DomainException.class, () -> openAccountUseCase.execute(
                new OpenAccountRequest("1000000001", customerId.getValue(), new BigDecimal("10.00"), "USD")));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
        Account unchanged = accountRepository.findByAccountNumber(AccountNumber.create("1000000001")).orElseThrow();
        assertEquals(seed.getId(), unchanged.getId());
        assertEquals(new BigDecimal("1500.00"), unchanged.getBalance().getAmount());
    }

    @Test
    @DisplayName("rejects an unknown customer")
    void rejectsAnUnknownCustomer() {
        DomainException exception = assertThrows(DomainException.class, () -> openAccountUseCase.execute(
                new OpenAccountRequest("2000000002", Id.generate().getValue(), new BigDecimal("0.00"), "USD")));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("rejects an account number that is not 10 digits")
    void rejectsAnAccountNumberThatIsNotTenDigits() {
        Id customerId = savedCustomer();

        DomainException exception = assertThrows(DomainException.class, () -> openAccountUseCase.execute(
                new OpenAccountRequest("123", customerId.getValue(), new BigDecimal("0.00"), "USD")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("closes an open account")
    void closesAnOpenAccount() {
        Id customerId = savedCustomer();
        AccountResponse opened = openAccountUseCase.execute(
                new OpenAccountRequest("2000000003", customerId.getValue(), new BigDecimal("25.00"), "USD"));

        AccountResponse closed = closeAccountUseCase.execute(opened.id());

        assertEquals("CLOSED", closed.status());
        Account persisted = accountRepository.findById(Id.create(opened.id())).orElseThrow();
        assertEquals(AccountStatus.CLOSED, persisted.getStatus());
        assertEquals(new BigDecimal("25.00"), persisted.getBalance().getAmount());
    }

    @Test
    @DisplayName("maintains the account number of an open account")
    void maintainsTheAccountNumberOfAnOpenAccount() {
        Id customerId = savedCustomer();
        AccountResponse opened = openAccountUseCase.execute(
                new OpenAccountRequest("2000000004", customerId.getValue(), new BigDecimal("0.00"), "USD"));

        AccountResponse maintained = maintainAccountUseCase.execute(
                new MaintainAccountRequest(opened.id(), "2000000005"));

        assertEquals("2000000005", maintained.accountNumber());
        assertTrue(accountRepository.findByAccountNumber(AccountNumber.create("2000000004")).isEmpty());
        assertTrue(accountRepository.findByAccountNumber(AccountNumber.create("2000000005")).isPresent());
    }

    @Test
    @DisplayName("rejects maintaining an account onto a number that already exists")
    void rejectsMaintainingAnAccountOntoANumberThatAlreadyExists() {
        Id customerId = savedCustomer();
        accountRepository.save(Account.create(
                Id.generate(),
                AccountNumber.create("1000000001"),
                customerId,
                Money.create(new BigDecimal("1500.00"), "USD")));
        AccountResponse opened = openAccountUseCase.execute(
                new OpenAccountRequest("2000000006", customerId.getValue(), new BigDecimal("0.00"), "USD"));

        DomainException exception = assertThrows(DomainException.class, () -> maintainAccountUseCase.execute(
                new MaintainAccountRequest(opened.id(), "1000000001")));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
        Account seed = accountRepository.findByAccountNumber(AccountNumber.create("1000000001")).orElseThrow();
        assertEquals(new BigDecimal("1500.00"), seed.getBalance().getAmount());
        assertEquals("2000000006", accountRepository.findById(Id.create(opened.id())).orElseThrow()
                .getAccountNumber().getValue());
    }

    private Id savedCustomer() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Jane Doe", customerId.getValue() + "@xyzbank.cl"));
        return customerId;
    }
}
