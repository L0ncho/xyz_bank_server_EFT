package cl.duoc.xyzbank.coreservice.accounts.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence.JpaAccountRepository;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The JPA account repository")
class JpaAccountRepositoryIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Saves an account and finds it by id
     * 2. Finds only the accounts owned by a given customer
     * 3. Returns an empty list when the customer owns no accounts
     * 4. Rejects two accounts with the same account number
     * 5. Rejects a save based on a stale version (optimistic lock conflict)
     */

    private static final AtomicLong accountNumbers = new AtomicLong(7_100_000_000L);

    @Autowired
    private JpaAccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @DisplayName("saves an account and finds it by id")
    void savesAnAccountAndFindsItById() {
        Id id = Id.generate();
        String accountNumber = nextAccountNumber();
        Account account = Account.create(
                id, AccountNumber.create(accountNumber), savedCustomer(),
                Money.create(new BigDecimal("100.00"), "USD"));

        accountRepository.save(account);
        Optional<Account> found = accountRepository.findById(id);

        assertTrue(found.isPresent());
        assertEquals(accountNumber, found.get().getAccountNumber().getValue());
    }

    @Test
    @DisplayName("finds only the accounts owned by a given customer")
    void findsOnlyTheAccountsOwnedByAGivenCustomer() {
        Id customerId = savedCustomer();
        Id otherCustomerId = savedCustomer();
        String ownedAccountNumber = nextAccountNumber();
        Account ownedAccount = Account.create(
                Id.generate(), AccountNumber.create(ownedAccountNumber), customerId,
                Money.create(new BigDecimal("50.00"), "USD"));
        Account otherAccount = Account.create(
                Id.generate(), AccountNumber.create(nextAccountNumber()), otherCustomerId,
                Money.create(new BigDecimal("75.00"), "USD"));
        accountRepository.save(ownedAccount);
        accountRepository.save(otherAccount);

        List<Account> accounts = accountRepository.findByCustomerId(customerId);

        assertEquals(1, accounts.size());
        assertEquals(ownedAccountNumber, accounts.get(0).getAccountNumber().getValue());
    }

    @Test
    @DisplayName("returns an empty list when the customer owns no accounts")
    void returnsAnEmptyListWhenTheCustomerOwnsNoAccounts() {
        List<Account> accounts = accountRepository.findByCustomerId(Id.generate());

        assertTrue(accounts.isEmpty());
    }

    @Test
    @DisplayName("rejects two accounts with the same account number")
    void rejectsTwoAccountsWithTheSameAccountNumber() {
        AccountNumber sharedNumber = AccountNumber.create(nextAccountNumber());
        Account first = Account.create(
                Id.generate(), sharedNumber, savedCustomer(),
                Money.create(new BigDecimal("10.00"), "USD"));
        Account second = Account.create(
                Id.generate(), sharedNumber, savedCustomer(),
                Money.create(new BigDecimal("20.00"), "USD"));
        accountRepository.save(first);

        assertThrows(DataIntegrityViolationException.class, () -> accountRepository.save(second));
    }

    @Test
    @DisplayName("rejects a save based on a stale version")
    void rejectsASaveBasedOnAStaleVersion() {
        Id id = Id.generate();
        Account original = Account.create(
                id, AccountNumber.create(nextAccountNumber()), savedCustomer(),
                Money.create(new BigDecimal("500.00"), "USD"));
        accountRepository.save(original);
        Account firstCopy = accountRepository.findById(id).orElseThrow();
        Account secondCopy = accountRepository.findById(id).orElseThrow();

        firstCopy.withdraw(Money.create(new BigDecimal("100.00"), "USD"), LocalDate.of(2026, 1, 1),
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(firstCopy);

        secondCopy.withdraw(Money.create(new BigDecimal("50.00"), "USD"), LocalDate.of(2026, 1, 1),
                Money.create(new BigDecimal("1000.00"), "USD"));
        DomainException exception = assertThrows(DomainException.class, () -> accountRepository.save(secondCopy));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
    }

    private Id savedCustomer() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(
                customerId, "Account Customer", customerId.getValue() + "@xyzbank.cl"));
        return customerId;
    }

    private String nextAccountNumber() {
        return Long.toString(accountNumbers.incrementAndGet());
    }
}
