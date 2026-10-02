package cl.duoc.xyzbank.coreservice.payments.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coreservice.payments.application.dto.ExternalPaymentRequest;
import cl.duoc.xyzbank.coreservice.payments.application.dto.ExternalPaymentResponse;
import cl.duoc.xyzbank.coreservice.payments.application.usecases.PayExternalRecipientUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The external payment use case")
class PayExternalRecipientUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
    private int accountSequence = 1;

    /*
     * Cases:
     * 1. Debits the payer and leaves every other account unchanged
     * 2. A second call with the same idempotency key does not debit again
     * 3. Amount 0.00 is a validation error and does not debit
     */

    @Test
    @DisplayName("debits the payer and leaves every other account unchanged")
    void debitsThePayerAndLeavesEveryOtherAccountUnchanged() {
        Account payer = anAccount("500.00");
        Account other = anAccount("200.00");

        ExternalPaymentResponse response = useCase().execute(payment(payer, "80.00", "payment-1"));

        assertEquals(new BigDecimal("420.00"), balanceOf(payer));
        assertEquals(new BigDecimal("200.00"), balanceOf(other));
        assertEquals("North Grid", response.recipient());
        assertEquals(0, payer.getDailyWithdrawnAmount().getAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("does not debit again when the same idempotency key is reused")
    void doesNotDebitAgainWhenTheSameIdempotencyKeyIsReused() {
        Account payer = anAccount("500.00");
        PayExternalRecipientUseCase useCase = useCase();
        ExternalPaymentRequest request = payment(payer, "80.00", "payment-repeat");

        ExternalPaymentResponse first = useCase.execute(request);
        ExternalPaymentResponse second = useCase.execute(request);

        assertEquals(first.transactionId(), second.transactionId());
        assertEquals(new BigDecimal("420.00"), balanceOf(payer));
    }

    @Test
    @DisplayName("rejects an amount of zero and does not debit the account")
    void rejectsAnAmountOfZeroAndDoesNotDebitTheAccount() {
        Account payer = anAccount("500.00");

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase().execute(payment(payer, "0.00", "payment-zero")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals("Amount must be positive", exception.getMessage());
        assertEquals(new BigDecimal("500.00"), balanceOf(payer));
    }

    private PayExternalRecipientUseCase useCase() {
        return new PayExternalRecipientUseCase(
                accountRepository,
                transactionRepository,
                new InMemoryPaymentLedger(accountRepository, transactionRepository),
                CLOCK);
    }

    private ExternalPaymentRequest payment(Account account, String amount, String idempotencyKey) {
        return new ExternalPaymentRequest(
                account.getId().getValue(), "North Grid", new BigDecimal(amount), "USD", idempotencyKey);
    }

    private Account anAccount(String balance) {
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(String.valueOf(4_000_000_000L + accountSequence++)),
                Id.generate(),
                Money.create(new BigDecimal(balance), "USD"));
        accountRepository.save(account);
        return account;
    }

    private BigDecimal balanceOf(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount();
    }
}
