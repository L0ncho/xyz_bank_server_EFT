package cl.duoc.xyzbank.coreservice.payments.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coreservice.payments.application.dto.DepositRequest;
import cl.duoc.xyzbank.coreservice.payments.application.dto.DepositResponse;
import cl.duoc.xyzbank.coreservice.payments.application.usecases.DepositAccountUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The deposit account use case")
class DepositAccountUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
    private int accountSequence = 1;

    /*
     * Cases:
     * 1. Credits the account
     * 2. A second call with the same idempotency key does not credit again
     * 3. Amount 0.00 is a validation error and does not credit
     */

    @Test
    @DisplayName("credits the account")
    void creditsTheAccount() {
        Account account = anAccount("500.00");

        DepositResponse response = useCase().execute(deposit(account, "100.00", "deposit-1"));

        assertEquals(new BigDecimal("600.00"), balanceOf(account));
        assertEquals(account.getId().getValue(), response.accountId());
        assertEquals(new BigDecimal("600.00"), response.newBalance());
    }

    @Test
    @DisplayName("does not credit again when the same idempotency key is reused")
    void doesNotCreditAgainWhenTheSameIdempotencyKeyIsReused() {
        Account account = anAccount("500.00");
        DepositAccountUseCase useCase = useCase();
        DepositRequest request = deposit(account, "100.00", "deposit-repeat");

        DepositResponse first = useCase.execute(request);
        DepositResponse second = useCase.execute(request);

        assertEquals(first.transactionId(), second.transactionId());
        assertEquals(new BigDecimal("600.00"), balanceOf(account));
    }

    @Test
    @DisplayName("rejects an amount of zero and does not credit the account")
    void rejectsAnAmountOfZeroAndDoesNotCreditTheAccount() {
        Account account = anAccount("500.00");

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase().execute(deposit(account, "0.00", "deposit-zero")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals("Amount must be positive", exception.getMessage());
        assertEquals(new BigDecimal("500.00"), balanceOf(account));
    }

    private DepositAccountUseCase useCase() {
        return new DepositAccountUseCase(
                accountRepository,
                transactionRepository,
                new InMemoryPaymentLedger(accountRepository, transactionRepository),
                CLOCK);
    }

    private DepositRequest deposit(Account account, String amount, String idempotencyKey) {
        return new DepositRequest(account.getId().getValue(), new BigDecimal(amount), "USD", idempotencyKey);
    }

    private Account anAccount(String balance) {
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(String.valueOf(3_000_000_000L + accountSequence++)),
                Id.generate(),
                Money.create(new BigDecimal(balance), "USD"));
        accountRepository.save(account);
        return account;
    }

    private BigDecimal balanceOf(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount();
    }
}
