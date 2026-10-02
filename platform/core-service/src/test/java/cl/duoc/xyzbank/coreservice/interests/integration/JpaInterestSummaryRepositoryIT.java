package cl.duoc.xyzbank.coreservice.interests.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence.JpaInterestSummaryRepository;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The JPA interest summary repository")
class JpaInterestSummaryRepositoryIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Saves a summary and finds it by account id and year
     * 2. Returns empty for an unknown account and year pair
     */

    private static final AtomicLong accountNumbers = new AtomicLong(7_300_000_000L);

    @Autowired
    private JpaInterestSummaryRepository interestSummaryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("saves a summary and finds it by account id and year")
    void savesASummaryAndFindsItByAccountIdAndYear() {
        Id accountId = savedAccount();
        AnnualInterestSummary summary = AnnualInterestSummary.create(
                Id.generate(), accountId, 2025,
                Money.create(new BigDecimal("1000.00"), "USD"),
                Money.create(new BigDecimal("1025.00"), "USD"),
                new BigDecimal("2.5000"),
                Money.create(new BigDecimal("25.00"), "USD"));

        interestSummaryRepository.save(summary);
        Optional<AnnualInterestSummary> found = interestSummaryRepository.findByAccountIdAndYear(accountId, 2025);

        assertTrue(found.isPresent());
        assertEquals(new BigDecimal("25.00"), found.get().getInterestAmount().getAmount());
    }

    @Test
    @DisplayName("returns empty for an unknown account and year pair")
    void returnsEmptyForAnUnknownAccountAndYearPair() {
        Optional<AnnualInterestSummary> found =
                interestSummaryRepository.findByAccountIdAndYear(Id.generate(), 2020);

        assertTrue(found.isEmpty());
    }

    private Id savedAccount() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(
                customerId, "Interest Customer", customerId.getValue() + "@xyzbank.cl"));
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId,
                AccountNumber.create(Long.toString(accountNumbers.incrementAndGet())),
                customerId,
                Money.create(new BigDecimal("1000.00"), "USD")));
        return accountId;
    }
}
