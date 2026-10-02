package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.DailyWithdrawalUsage;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("An account movement")
class AccountMovementTest {

    /*
     * Cases:
     * 1. Debit reduces the balance
     * 2. Debit leaves the daily withdrawal usage unchanged
     * 3. A closed account rejects a debit and keeps its balance
     */

    @Test
    @DisplayName("debit reduces the balance")
    void debitReducesTheBalance() {
        Account account = anOpenAccount("500.00");

        account.debit(Money.create(new BigDecimal("100.00"), "USD"));

        assertEquals(new BigDecimal("400.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("debit leaves the daily withdrawal usage unchanged")
    void debitLeavesTheDailyWithdrawalUsageUnchanged() {
        Account account = anOpenAccount("500.00");

        account.debit(Money.create(new BigDecimal("100.00"), "USD"));

        assertEquals(new BigDecimal("40.00"), account.getDailyWithdrawnAmount().getAmount());
        assertEquals(Optional.of(LocalDate.parse("2026-01-01")), account.getDailyWithdrawnDate());
    }

    @Test
    @DisplayName("a closed account rejects a debit and keeps its balance")
    void aClosedAccountRejectsADebitAndKeepsItsBalance() {
        Account account = anOpenAccount("500.00");
        account.close();

        DomainException exception = assertThrows(
                DomainException.class,
                () -> account.debit(Money.create(new BigDecimal("100.00"), "USD")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("500.00"), account.getBalance().getAmount());
    }

    private Account anOpenAccount(String balance) {
        return Account.create(
                Id.generate(),
                AccountNumber.create("1234567890"),
                Id.generate(),
                Money.create(new BigDecimal(balance), "USD"),
                0L,
                DailyWithdrawalUsage.create(
                        Money.create(new BigDecimal("40.00"), "USD"),
                        Optional.of(LocalDate.parse("2026-01-01"))));
    }
}
