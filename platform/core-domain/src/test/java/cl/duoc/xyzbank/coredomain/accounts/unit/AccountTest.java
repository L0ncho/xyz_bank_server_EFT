package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The Account")
class AccountTest {

    /*
     * Cases:
     * 1. Creates with a valid account number, owner id, balance, and currency
     * 2. Exposes its balance as a Money value object
     * 3. Withdraw reduces the balance when within the balance and the daily limit
     * 4. Withdraw allows withdrawing exactly the full balance
     * 5. Withdraw rejects an amount greater than the current balance
     * 6. Withdraw rejects a single withdrawal that exceeds the daily limit
     * 7. Withdraw rejects a further withdrawal that would push the day's cumulative total over the limit
     * 8. Withdraw allows cumulative withdrawals that stay within the daily limit
     * 9. Withdraw resets the cumulative daily total on a new calendar day
     * 10. Withdraw rejects an amount whose currency does not match the account's balance currency
     * 11. Credit increases the balance by the credited amount
     * 12. Credit rejects an amount whose currency does not match the account's balance currency
     * 13. Reversing a credit lowers the balance and leaves the daily withdrawal usage unchanged
     * 14. Reversing a credit rejects an amount that would make the balance negative
     */

    @Test
    @DisplayName("creates with a valid account number, owner id, balance, and currency")
    void createsWithAValidAccountNumberOwnerIdBalanceAndCurrency() {
        Id id = Id.generate();
        Id customerId = Id.generate();
        AccountNumber accountNumber = AccountNumber.create("1234567890");
        Money balance = Money.create(new BigDecimal("100.00"), "USD");

        Account account = Account.create(id, accountNumber, customerId, balance);

        assertEquals(id, account.getId());
        assertEquals(accountNumber, account.getAccountNumber());
        assertEquals(customerId, account.getCustomerId());
        assertEquals(balance, account.getBalance());
    }

    @Test
    @DisplayName("exposes its balance as a Money value object")
    void exposesItsBalanceAsAMoneyValueObject() {
        Money balance = Money.create(new BigDecimal("250.50"), "CLP");

        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);

        assertEquals(new BigDecimal("250.50"), account.getBalance().getAmount());
        assertEquals("CLP", account.getBalance().getCurrency());
    }

    @Test
    @DisplayName("withdraw reduces the balance when within the balance and the daily limit")
    void withdrawReducesTheBalanceWhenWithinTheBalanceAndTheDailyLimit() {
        Money balance = Money.create(new BigDecimal("500.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("100.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit);

        assertEquals(new BigDecimal("400.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw rejects an amount greater than the current balance")
    void withdrawRejectsAnAmountGreaterThanTheCurrentBalance() {
        Money balance = Money.create(new BigDecimal("50.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("100.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        DomainException exception = assertThrows(
                DomainException.class, () -> account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("50.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw allows withdrawing exactly the full balance")
    void withdrawAllowsWithdrawingExactlyTheFullBalance() {
        Money balance = Money.create(new BigDecimal("50.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("50.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit);

        assertEquals(new BigDecimal("0.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw rejects a single withdrawal that exceeds the daily limit")
    void withdrawRejectsASingleWithdrawalThatExceedsTheDailyLimit() {
        Money balance = Money.create(new BigDecimal("5000.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("1500.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        DomainException exception = assertThrows(
                DomainException.class, () -> account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("5000.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw rejects a further withdrawal that would push the day's cumulative total over the limit")
    void withdrawRejectsAFurtherWithdrawalThatWouldPushTheDaysCumulativeTotalOverTheLimit() {
        Money balance = Money.create(new BigDecimal("5000.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");
        LocalDate today = LocalDate.of(2026, 1, 1);
        account.withdraw(Money.create(new BigDecimal("700.00"), "USD"), today, dailyLimit);

        DomainException exception = assertThrows(DomainException.class, () -> account.withdraw(
                Money.create(new BigDecimal("400.00"), "USD"), today, dailyLimit));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("4300.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw allows cumulative withdrawals that stay within the daily limit")
    void withdrawAllowsCumulativeWithdrawalsThatStayWithinTheDailyLimit() {
        Money balance = Money.create(new BigDecimal("5000.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");
        LocalDate today = LocalDate.of(2026, 1, 1);
        account.withdraw(Money.create(new BigDecimal("700.00"), "USD"), today, dailyLimit);

        account.withdraw(Money.create(new BigDecimal("300.00"), "USD"), today, dailyLimit);

        assertEquals(new BigDecimal("4000.00"), account.getBalance().getAmount());
        assertEquals(new BigDecimal("1000.00"), account.getDailyWithdrawnAmount().getAmount());
    }

    @Test
    @DisplayName("withdraw resets the cumulative daily total on a new calendar day")
    void withdrawResetsTheCumulativeDailyTotalOnANewCalendarDay() {
        Money balance = Money.create(new BigDecimal("5000.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");
        account.withdraw(Money.create(new BigDecimal("1000.00"), "USD"), LocalDate.of(2026, 1, 1), dailyLimit);

        account.withdraw(Money.create(new BigDecimal("900.00"), "USD"), LocalDate.of(2026, 1, 2), dailyLimit);

        assertEquals(new BigDecimal("900.00"), account.getDailyWithdrawnAmount().getAmount());
        assertEquals(new BigDecimal("3100.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw rejects an amount whose currency does not match the account's balance currency")
    void withdrawRejectsAnAmountWhoseCurrencyDoesNotMatchTheAccountsBalanceCurrency() {
        Money balance = Money.create(new BigDecimal("500.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("100.00"), "CLP");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        DomainException exception = assertThrows(
                DomainException.class, () -> account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("500.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("credit increases the balance by the credited amount")
    void creditIncreasesTheBalanceByTheCreditedAmount() {
        Money balance = Money.create(new BigDecimal("500.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("35.00"), "USD");

        account.credit(amount);

        assertEquals(new BigDecimal("535.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("credit rejects an amount whose currency does not match the account's balance currency")
    void creditRejectsAnAmountWhoseCurrencyDoesNotMatchTheAccountsBalanceCurrency() {
        Money balance = Money.create(new BigDecimal("500.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("35.00"), "CLP");

        DomainException exception = assertThrows(DomainException.class, () -> account.credit(amount));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("500.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("reversing a credit lowers the balance and leaves the daily withdrawal usage unchanged")
    void reversingACreditLowersTheBalanceAndLeavesTheDailyWithdrawalUsageUnchanged() {
        Money balance = Money.create(new BigDecimal("500.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money withdrawal = Money.create(new BigDecimal("40.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");
        account.withdraw(withdrawal, LocalDate.of(2026, 1, 1), dailyLimit);

        account.reverseCredit(Money.create(new BigDecimal("35.00"), "USD"));

        assertEquals(new BigDecimal("425.00"), account.getBalance().getAmount());
        assertEquals(new BigDecimal("40.00"), account.getDailyWithdrawnAmount().getAmount());
    }

    @Test
    @DisplayName("reversing a credit rejects an amount that would make the balance negative")
    void reversingACreditRejectsAnAmountThatWouldMakeTheBalanceNegative() {
        Money balance = Money.create(new BigDecimal("10.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> account.reverseCredit(Money.create(new BigDecimal("35.00"), "USD")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("10.00"), account.getBalance().getAmount());
    }
}
