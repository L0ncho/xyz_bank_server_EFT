package cl.duoc.xyzbank.coreservice.payments.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coreservice.payments.application.dto.TransferRequest;
import cl.duoc.xyzbank.coreservice.payments.application.dto.TransferResponse;
import cl.duoc.xyzbank.coreservice.payments.application.usecases.TransferBetweenAccountsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The transfer between accounts use case")
class TransferBetweenAccountsUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
    private int accountSequence = 1;

    /*
     * Cases:
     * 1. Moves money from the source account to the destination account
     * 2. Does not debit the source when the destination is closed
     * 3. Does not debit the source when the destination does not exist
     * 4. A second call with the same idempotency key does not move money again
     * 5. Amount 0.00 is a validation error and does not move money
     */

    @Test
    @DisplayName("moves money from the source account to the destination account")
    void movesMoneyFromTheSourceAccountToTheDestinationAccount() {
        Account source = anAccount("500.00");
        Account destination = anAccount("200.00");

        TransferResponse response = useCase().execute(transfer(source, destination, "100.00", "transfer-1"));

        assertEquals(new BigDecimal("400.00"), balanceOf(source));
        assertEquals(new BigDecimal("300.00"), balanceOf(destination));
        assertEquals(source.getId().getValue(), response.sourceAccountId());
        assertEquals(destination.getId().getValue(), response.destinationAccountId());
    }

    @Test
    @DisplayName("does not debit the source when the destination is closed")
    void doesNotDebitTheSourceWhenTheDestinationIsClosed() {
        Account source = anAccount("500.00");
        Account destination = anAccount("200.00");
        destination.close();

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase().execute(transfer(source, destination, "100.00", "transfer-closed")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("500.00"), balanceOf(source));
        assertEquals(new BigDecimal("200.00"), balanceOf(destination));
    }

    @Test
    @DisplayName("does not debit the source when the destination does not exist")
    void doesNotDebitTheSourceWhenTheDestinationDoesNotExist() {
        Account source = anAccount("500.00");
        Id missingDestinationId = Id.generate();

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase().execute(new TransferRequest(
                        source.getId().getValue(),
                        missingDestinationId.getValue(),
                        new BigDecimal("100.00"),
                        "USD",
                        "transfer-missing")));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
        assertEquals(new BigDecimal("500.00"), balanceOf(source));
    }

    @Test
    @DisplayName("does not move money again when the same idempotency key is reused")
    void doesNotMoveMoneyAgainWhenTheSameIdempotencyKeyIsReused() {
        Account source = anAccount("500.00");
        Account destination = anAccount("200.00");
        TransferBetweenAccountsUseCase useCase = useCase();
        TransferRequest request = transfer(source, destination, "100.00", "transfer-repeat");

        TransferResponse first = useCase.execute(request);
        TransferResponse second = useCase.execute(request);

        assertEquals(first.transactionId(), second.transactionId());
        assertEquals(new BigDecimal("400.00"), balanceOf(source));
        assertEquals(new BigDecimal("300.00"), balanceOf(destination));
        assertEquals(new BigDecimal("400.00"), second.sourceBalance());
        assertEquals(new BigDecimal("300.00"), second.destinationBalance());
    }

    @Test
    @DisplayName("rejects an amount of zero and does not move money")
    void rejectsAnAmountOfZeroAndDoesNotMoveMoney() {
        Account source = anAccount("500.00");
        Account destination = anAccount("200.00");

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase().execute(transfer(source, destination, "0.00", "transfer-zero")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals("Amount must be positive", exception.getMessage());
        assertEquals(new BigDecimal("500.00"), balanceOf(source));
        assertEquals(new BigDecimal("200.00"), balanceOf(destination));
    }

    private TransferBetweenAccountsUseCase useCase() {
        return new TransferBetweenAccountsUseCase(
                accountRepository,
                transactionRepository,
                new InMemoryPaymentLedger(accountRepository, transactionRepository),
                CLOCK);
    }

    private TransferRequest transfer(Account source, Account destination, String amount, String idempotencyKey) {
        return new TransferRequest(
                source.getId().getValue(),
                destination.getId().getValue(),
                new BigDecimal(amount),
                "USD",
                idempotencyKey);
    }

    private Account anAccount(String balance) {
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(String.valueOf(2_000_000_000L + accountSequence++)),
                Id.generate(),
                Money.create(new BigDecimal(balance), "USD"));
        accountRepository.save(account);
        return account;
    }

    private BigDecimal balanceOf(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount();
    }
}
