package cl.duoc.xyzbank.coreservice.interests.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.unit.InMemoryInterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditResponse;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.ReverseInterestCreditUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The reverse interest credit use case")
class ReverseInterestCreditUseCaseTest {

    /*
     * Cases:
     * 1. Reversing an applied credit lowers the balance and publishes InterestCreditReversed
     * 2. A second reversal does not lower the balance again
     * 3. A year without an applied credit is rejected
     * 4. An amount that would make the balance negative leaves the balance unchanged
     */

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
    private final InMemoryInterestSummaryRepository interestSummaryRepository = new InMemoryInterestSummaryRepository();
    private final InMemoryInterestCreditRepository interestCreditRepository =
            new InMemoryInterestCreditRepository(accountRepository, transactionRepository, interestSummaryRepository);
    private final InMemoryInterestCreditResultPublisher results = new InMemoryInterestCreditResultPublisher();

    @Test
    @DisplayName("lowers the balance of an applied interest credit")
    void lowersTheBalanceOfAnAppliedInterestCredit() {
        Id accountId = anAppliedCredit("1035.00", "35.00");
        ReverseInterestCreditUseCase useCase = useCase();

        InterestCreditResponse response = useCase.execute(accountId.getValue(), 2025);

        assertEquals(new BigDecimal("1000.00"), response.newBalance());
        assertEquals(1, results.reversals().size());
        assertEquals(accountId.getValue(), results.reversals().getFirst().accountId());
        assertEquals("interest:" + accountId.getValue() + ":2025:reversed", results.reversals().getFirst().eventId());
    }

    @Test
    @DisplayName("does not lower the balance again when the same credit is reversed twice")
    void doesNotLowerTheBalanceAgainWhenTheSameCreditIsReversedTwice() {
        Id accountId = anAppliedCredit("1035.00", "35.00");
        ReverseInterestCreditUseCase useCase = useCase();

        useCase.execute(accountId.getValue(), 2025);
        InterestCreditResponse second = useCase.execute(accountId.getValue(), 2025);

        assertEquals(new BigDecimal("1000.00"), second.newBalance());
        assertEquals(1, results.reversals().size());
    }

    @Test
    @DisplayName("rejects a year that has no applied interest credit")
    void rejectsAYearThatHasNoAppliedInterestCredit() {
        Id accountId = anAccount("1000.00");
        ReverseInterestCreditUseCase useCase = useCase();

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.execute(accountId.getValue(), 2025));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
        assertEquals(new BigDecimal("1000.00"), accountRepository.findById(accountId).orElseThrow().getBalance().getAmount());
    }

    @Test
    @DisplayName("leaves the balance unchanged when the reversal would make it negative")
    void leavesTheBalanceUnchangedWhenTheReversalWouldMakeItNegative() {
        Id accountId = anAppliedCredit("10.00", "35.00");
        ReverseInterestCreditUseCase useCase = useCase();

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.execute(accountId.getValue(), 2025));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("10.00"), accountRepository.findById(accountId).orElseThrow().getBalance().getAmount());
        assertEquals(0, results.reversals().size());
    }

    private ReverseInterestCreditUseCase useCase() {
        return new ReverseInterestCreditUseCase(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                interestCreditRepository,
                results,
                CLOCK);
    }

    private Id anAppliedCredit(String balance, String interest) {
        Id accountId = anAccount(balance);
        interestSummaryRepository.save(AnnualInterestSummary.create(
                Id.generate(),
                accountId,
                2025,
                Money.create(new BigDecimal("1000.00"), "USD"),
                Money.create(new BigDecimal("1035.00"), "USD"),
                new BigDecimal("0.035"),
                Money.create(new BigDecimal(interest), "USD")));
        return accountId;
    }

    private Id anAccount(String balance) {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId,
                AccountNumber.create("1234567890"),
                Id.generate(),
                Money.create(new BigDecimal(balance), "USD")));
        return accountId;
    }
}
