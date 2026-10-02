package cl.duoc.xyzbank.coreservice.interests.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.interests.unit.InMemoryInterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestCreditRepository;
import cl.duoc.xyzbank.coreservice.interests.application.dto.CreditInterestRequest;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditRejected;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditResponse;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.CreditInterestUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The CreditInterest use case")
class CreditInterestUseCaseTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
    private final InMemoryInterestSummaryRepository interestSummaryRepository = new InMemoryInterestSummaryRepository();
    private final InMemoryInterestCreditRepository interestCreditRepository =
            new InMemoryInterestCreditRepository(accountRepository, transactionRepository, interestSummaryRepository);

    /*
     * Cases:
     * 1. Successful credit increases the balance and returns the new balance and a transaction id
     * 2. Throws not found for an unknown account
     * 3. Throws validation for a missing idempotency key
     * 4. Throws validation for a non-positive amount
     * 5. Throws validation for a currency mismatch
     * 6. Repeated idempotency key with the same account and amount replays the original result
     * 7. Repeated idempotency key with a different amount throws a conflict, leaving the balance unchanged
     * 8. Throws a conflict when the year already has an interest summary credited under a different key
     * 9. A duplicate InterestCalculated event (same eventId) credits the balance only once
     * 10. An invalid amount publishes InterestCreditRejected and leaves the balance unchanged
     * 11. An unknown account publishes InterestCreditRejected and does not credit
     * 12. A year already credited publishes InterestCreditRejected and leaves the balance unchanged
     * 13. A technical failure publishes nothing and propagates
     */

    @Test
    @DisplayName("increases the balance and returns the new balance and a transaction id")
    void increasesTheBalanceAndReturnsTheNewBalanceAndATransactionId() {
        Id accountId = anExistingAccount("1000.00");
        CreditInterestUseCase useCase = useCase();

        InterestCreditResponse response = useCase.execute(aRequest(accountId, "35.00", "key-1"));

        assertEquals(new BigDecimal("1035.00"), response.newBalance());
        assertEquals(accountId.getValue(), response.accountId());
        assertEquals(new BigDecimal("35.00"), response.amount());
        assertEquals(2025, response.year());
    }

    @Test
    @DisplayName("throws not found for an unknown account")
    void throwsNotFoundForAnUnknownAccount() {
        CreditInterestUseCase useCase = useCase();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                aRequest(Id.generate(), "10.00", "key-2")));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a missing idempotency key")
    void throwsValidationForAMissingIdempotencyKey() {
        Id accountId = anExistingAccount("1000.00");
        CreditInterestUseCase useCase = useCase();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                aRequest(accountId, "10.00", null)));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a non-positive amount")
    void throwsValidationForANonPositiveAmount() {
        Id accountId = anExistingAccount("1000.00");
        CreditInterestUseCase useCase = useCase();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                aRequest(accountId, "0.00", "key-3")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a currency mismatch")
    void throwsValidationForACurrencyMismatch() {
        Id accountId = anExistingAccount("1000.00");
        CreditInterestUseCase useCase = useCase();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new CreditInterestRequest(
                        accountId.getValue(),
                        2025,
                        new BigDecimal("35.00"),
                        "CLP",
                        new BigDecimal("0.035"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("1035.00"),
                        "key-4")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("replays the original result for a repeated idempotency key with the same account and amount")
    void replaysTheOriginalResultForARepeatedIdempotencyKeyWithTheSameAccountAndAmount() {
        Id accountId = anExistingAccount("1000.00");
        CreditInterestUseCase useCase = useCase();
        InterestCreditResponse first = useCase.execute(aRequest(accountId, "35.00", "key-5"));

        InterestCreditResponse replay = useCase.execute(aRequest(accountId, "35.00", "key-5"));

        assertEquals(first.transactionId(), replay.transactionId());
        assertEquals(new BigDecimal("1035.00"), replay.newBalance());
    }

    @Test
    @DisplayName("throws a conflict for a repeated idempotency key with a different amount, leaving the balance unchanged")
    void throwsAConflictForARepeatedIdempotencyKeyWithADifferentAmount() {
        Id accountId = anExistingAccount("1000.00");
        CreditInterestUseCase useCase = useCase();
        useCase.execute(aRequest(accountId, "35.00", "key-6"));

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                aRequest(accountId, "20.00", "key-6")));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
        assertEquals(
                new BigDecimal("1035.00"),
                accountRepository.findById(accountId).orElseThrow().getBalance().getAmount());
    }

    @Test
    @DisplayName("throws a conflict when the year already has an interest summary credited under a different key")
    void throwsAConflictWhenTheYearAlreadyHasAnInterestSummaryCreditedUnderADifferentKey() {
        Id accountId = anExistingAccount("1000.00");
        CreditInterestUseCase useCase = useCase();
        useCase.execute(aRequest(accountId, "35.00", "key-7a"));

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                aRequest(accountId, "35.00", "key-7b")));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
        assertEquals(
                new BigDecimal("1035.00"),
                accountRepository.findById(accountId).orElseThrow().getBalance().getAmount());
    }

    @Test
    @DisplayName("credits the balance only once when the same interest calculated event arrives twice")
    void creditsTheBalanceOnlyOnceWhenTheSameInterestCalculatedEventArrivesTwice() {
        Id accountId = anExistingAccount("1000.00");
        String eventId = "interest:" + accountId.getValue() + ":2025";
        InMemoryProcessedInterestEventRepository processedInterestEvents =
                new InMemoryProcessedInterestEventRepository();
        CreditInterestUseCase useCase = new CreditInterestUseCase(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                interestCreditRepository,
                processedInterestEvents,
                CLOCK);

        useCase.execute(aRequest(accountId, "35.00", eventId), eventId);
        useCase.execute(aRequest(accountId, "35.00", eventId), eventId);

        assertEquals(
                new BigDecimal("1035.00"),
                accountRepository.findById(accountId).orElseThrow().getBalance().getAmount());
        assertTrue(processedInterestEvents.findIdempotencyKey(eventId).isPresent());
    }

    @Test
    @DisplayName("publishes InterestCreditRejected and leaves the balance unchanged for an invalid amount")
    void publishesInterestCreditRejectedAndLeavesTheBalanceUnchangedForAnInvalidAmount() {
        Id accountId = anExistingAccount("1000.00");
        String eventId = "interest:" + accountId.getValue() + ":2025";
        InMemoryInterestCreditResultPublisher results = new InMemoryInterestCreditResultPublisher();
        CreditInterestUseCase useCase = useCasePublishing(results);

        useCase.executeFromEvent(aRequest(accountId, "0.00", "delivery-invalid"), eventId);

        assertEquals(
                new BigDecimal("1000.00"),
                accountRepository.findById(accountId).orElseThrow().getBalance().getAmount());
        assertEquals(
                List.of(new InterestCreditRejected(eventId, accountId.getValue(), "Amount must be positive")),
                results.rejections());
    }

    @Test
    @DisplayName("publishes InterestCreditRejected when the account does not exist")
    void publishesInterestCreditRejectedWhenTheAccountDoesNotExist() {
        Id accountId = Id.generate();
        String eventId = "interest:" + accountId.getValue() + ":2025";
        InMemoryInterestCreditResultPublisher results = new InMemoryInterestCreditResultPublisher();
        CreditInterestUseCase useCase = useCasePublishing(results);

        useCase.executeFromEvent(aRequest(accountId, "10.00", "delivery-missing"), eventId);

        assertEquals(
                List.of(new InterestCreditRejected(
                        eventId, accountId.getValue(), "Account " + accountId.getValue() + " not found")),
                results.rejections());
    }

    @Test
    @DisplayName("publishes InterestCreditRejected when the year is already credited")
    void publishesInterestCreditRejectedWhenTheYearIsAlreadyCredited() {
        Id accountId = anExistingAccount("1000.00");
        String eventId = "interest:" + accountId.getValue() + ":2025";
        InMemoryInterestCreditResultPublisher results = new InMemoryInterestCreditResultPublisher();
        CreditInterestUseCase useCase = useCasePublishing(results);
        useCase.execute(aRequest(accountId, "35.00", "interest-" + accountId.getValue() + "-2025"));

        useCase.executeFromEvent(aRequest(accountId, "35.00", eventId), eventId);

        assertEquals(
                new BigDecimal("1035.00"),
                accountRepository.findById(accountId).orElseThrow().getBalance().getAmount());
        assertEquals(1, results.rejections().size());
        assertEquals(eventId, results.rejections().get(0).eventId());
        assertTrue(results.rejections().get(0).reason().contains("Interest already credited"));
    }

    @Test
    @DisplayName("publishes nothing and propagates when the credit fails for a technical reason")
    void publishesNothingAndPropagatesWhenTheCreditFailsForATechnicalReason() {
        Id accountId = anExistingAccount("1000.00");
        String eventId = "interest:" + accountId.getValue() + ":2025";
        InMemoryInterestCreditResultPublisher results = new InMemoryInterestCreditResultPublisher();
        CreditInterestUseCase useCase = new CreditInterestUseCase(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                failingInterestCreditRepository(),
                new InMemoryProcessedInterestEventRepository(),
                CLOCK,
                results);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                useCase.executeFromEvent(aRequest(accountId, "35.00", "delivery-technical"), eventId));

        assertEquals("database unavailable", exception.getMessage());
        assertTrue(results.rejections().isEmpty());
    }

    private CreditInterestUseCase useCasePublishing(InMemoryInterestCreditResultPublisher results) {
        return new CreditInterestUseCase(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                interestCreditRepository,
                new InMemoryProcessedInterestEventRepository(),
                CLOCK,
                results);
    }

    private InterestCreditRepository failingInterestCreditRepository() {
        return new InterestCreditRepository() {
            @Override
            public void persistInterestCredit(
                    cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account account,
                    cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction transaction,
                    cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary summary) {
                throw new IllegalStateException("database unavailable");
            }

            @Override
            public void persistInterestReversal(
                    cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account account,
                    cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction transaction) {
            }
        };
    }

    private Id anExistingAccount(String balance) {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId,
                AccountNumber.create("1234567890"),
                Id.generate(),
                Money.create(new BigDecimal(balance), "USD")));
        return accountId;
    }

    private CreditInterestRequest aRequest(Id accountId, String amount, String idempotencyKey) {
        BigDecimal creditAmount = new BigDecimal(amount);
        return new CreditInterestRequest(
                accountId.getValue(),
                2025,
                creditAmount,
                "USD",
                new BigDecimal("0.035"),
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00").add(creditAmount),
                idempotencyKey);
    }

    private CreditInterestUseCase useCase() {
        return new CreditInterestUseCase(
                accountRepository, transactionRepository, interestSummaryRepository, interestCreditRepository, CLOCK);
    }
}
