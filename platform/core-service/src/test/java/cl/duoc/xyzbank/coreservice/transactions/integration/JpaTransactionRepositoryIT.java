package cl.duoc.xyzbank.coreservice.transactions.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.Cursor;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.DateRange;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionPage;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coreservice.transactions.infrastructure.persistence.JpaTransactionRepository;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The JPA transaction repository")
class JpaTransactionRepositoryIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Saves a transaction and finds it by id
     * 2. Finds only the transactions owned by a given account, most-recent-first
     * 3. Filters by date range and type
     * 4. Continues across a page boundary with no gap or repeat
     * 5. Rejects a malformed cursor
     * 6. Finds a transaction by its idempotency key
     * 7. Rejects two transactions with the same idempotency key
     */

    private static final AtomicLong accountNumbers = new AtomicLong(7_200_000_000L);

    @Autowired
    private JpaTransactionRepository transactionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("saves a transaction and finds it by id")
    void savesATransactionAndFindsItById() {
        Id id = Id.generate();
        Transaction transaction = Transaction.create(
                id, savedAccount(), TransactionType.DEBIT,
                Money.create(new BigDecimal("42.00"), "USD"), LocalDate.of(2026, 1, 5), "Groceries");

        transactionRepository.save(transaction);
        Optional<Transaction> found = transactionRepository.findById(id);

        assertTrue(found.isPresent());
        assertEquals("Groceries", found.get().getDescription());
    }

    @Test
    @DisplayName("finds only the transactions owned by a given account, most-recent-first")
    void findsOnlyTheTransactionsOwnedByAGivenAccountMostRecentFirst() {
        Id accountId = savedAccount();
        Id otherAccountId = savedAccount();
        save(accountId, TransactionType.DEBIT, "10.00", LocalDate.of(2026, 1, 1));
        save(accountId, TransactionType.CREDIT, "20.00", LocalDate.of(2026, 1, 3));
        save(otherAccountId, TransactionType.DEBIT, "30.00", LocalDate.of(2026, 1, 2));

        TransactionPage page = transactionRepository.findByAccountId(
                accountId, DateRange.create(Optional.empty(), Optional.empty()), Optional.empty(), Optional.empty(), 10);

        assertEquals(2, page.getItems().size());
        assertEquals(LocalDate.of(2026, 1, 3), page.getItems().get(0).getOccurredOn());
        assertEquals(LocalDate.of(2026, 1, 1), page.getItems().get(1).getOccurredOn());
    }

    @Test
    @DisplayName("filters by date range and type")
    void filtersByDateRangeAndType() {
        Id accountId = savedAccount();
        save(accountId, TransactionType.DEBIT, "10.00", LocalDate.of(2026, 1, 1));
        save(accountId, TransactionType.CREDIT, "20.00", LocalDate.of(2026, 1, 15));
        save(accountId, TransactionType.DEBIT, "30.00", LocalDate.of(2026, 1, 31));

        TransactionPage page = transactionRepository.findByAccountId(
                accountId,
                DateRange.create(Optional.of(LocalDate.of(2026, 1, 10)), Optional.of(LocalDate.of(2026, 1, 31))),
                Optional.of(TransactionType.DEBIT),
                Optional.empty(),
                10);

        assertEquals(1, page.getItems().size());
        assertEquals(LocalDate.of(2026, 1, 31), page.getItems().get(0).getOccurredOn());
    }

    @Test
    @DisplayName("continues across a page boundary with no gap or repeat")
    void continuesAcrossAPageBoundaryWithNoGapOrRepeat() {
        Id accountId = savedAccount();
        for (int day = 1; day <= 5; day++) {
            save(accountId, TransactionType.DEBIT, "10.00", LocalDate.of(2026, 1, day));
        }

        TransactionPage firstPage = transactionRepository.findByAccountId(
                accountId, DateRange.create(Optional.empty(), Optional.empty()), Optional.empty(), Optional.empty(), 3);
        assertEquals(3, firstPage.getItems().size());
        assertTrue(firstPage.getNextCursor().isPresent());

        TransactionPage secondPage = transactionRepository.findByAccountId(
                accountId, DateRange.create(Optional.empty(), Optional.empty()), Optional.empty(),
                firstPage.getNextCursor(), 3);

        assertEquals(2, secondPage.getItems().size());
        assertTrue(secondPage.getNextCursor().isEmpty());
        List<LocalDate> allDates = List.of(
                firstPage.getItems().get(0).getOccurredOn(),
                firstPage.getItems().get(1).getOccurredOn(),
                firstPage.getItems().get(2).getOccurredOn(),
                secondPage.getItems().get(0).getOccurredOn(),
                secondPage.getItems().get(1).getOccurredOn());
        assertEquals(5, allDates.stream().distinct().count());
    }

    @Test
    @DisplayName("rejects a malformed cursor")
    void rejectsAMalformedCursor() {
        Id accountId = Id.generate();

        DomainException exception = assertThrows(DomainException.class, () -> transactionRepository.findByAccountId(
                accountId, DateRange.create(Optional.empty(), Optional.empty()), Optional.empty(),
                Optional.of(Cursor.create("not-a-real-cursor")), 10));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("finds a transaction by its idempotency key")
    void findsATransactionByItsIdempotencyKey() {
        String idempotencyKey = Id.generate().getValue();
        Transaction transaction = Transaction.create(
                Id.generate(), savedAccount(), TransactionType.DEBIT,
                Money.create(new BigDecimal("25.00"), "USD"), LocalDate.of(2026, 1, 1), null,
                Optional.of(idempotencyKey));
        transactionRepository.save(transaction);

        Optional<Transaction> found = transactionRepository.findByIdempotencyKey(idempotencyKey);
        Optional<Transaction> notFound = transactionRepository.findByIdempotencyKey(Id.generate().getValue());

        assertTrue(found.isPresent());
        assertEquals(new BigDecimal("25.00"), found.get().getAmount().getAmount());
        assertTrue(notFound.isEmpty());
    }

    @Test
    @DisplayName("rejects two transactions with the same idempotency key")
    void rejectsTwoTransactionsWithTheSameIdempotencyKey() {
        Id accountId = savedAccount();
        String idempotencyKey = Id.generate().getValue();
        Transaction first = Transaction.create(
                Id.generate(), accountId, TransactionType.DEBIT,
                Money.create(new BigDecimal("10.00"), "USD"), LocalDate.of(2026, 1, 1), null,
                Optional.of(idempotencyKey));
        Transaction second = Transaction.create(
                Id.generate(), accountId, TransactionType.DEBIT,
                Money.create(new BigDecimal("20.00"), "USD"), LocalDate.of(2026, 1, 1), null,
                Optional.of(idempotencyKey));
        transactionRepository.save(first);

        DomainException exception = assertThrows(DomainException.class, () -> transactionRepository.save(second));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
    }

    private void save(Id accountId, TransactionType type, String amount, LocalDate occurredOn) {
        transactionRepository.save(Transaction.create(
                Id.generate(), accountId, type, Money.create(new BigDecimal(amount), "USD"), occurredOn, null));
    }

    private Id savedAccount() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(
                customerId, "Transaction Customer", customerId.getValue() + "@xyzbank.cl"));
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(Long.toString(accountNumbers.incrementAndGet())),
                customerId,
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(account);
        return account.getId();
    }
}
