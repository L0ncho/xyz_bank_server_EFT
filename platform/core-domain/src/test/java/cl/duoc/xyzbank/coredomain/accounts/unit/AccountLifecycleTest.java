package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountStatus;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The account lifecycle")
class AccountLifecycleTest {

    /*
     * Cases:
     * 1. Opening an account leaves it open
     * 2. An account created for an existing row is open
     * 3. Closing an open account marks it closed
     * 4. Closing an already closed account is rejected
     * 5. A closed account rejects a withdrawal
     * 6. A closed account rejects a credit
     * 7. A closed account rejects a credit reversal
     * 8. Maintaining an open account replaces its account number
     * 9. Maintaining a closed account is rejected
     */

    @Test
    @DisplayName("opening an account leaves it open")
    void openingAnAccountLeavesItOpen() {
        Account account = Account.open(
                Id.generate(),
                AccountNumber.create("1234567890"),
                Id.generate(),
                Money.create(new BigDecimal("0.00"), "USD"));

        assertEquals(AccountStatus.OPEN, account.getStatus());
        assertEquals("1234567890", account.getAccountNumber().getValue());
    }

    @Test
    @DisplayName("an account created for an existing row is open")
    void anAccountCreatedForAnExistingRowIsOpen() {
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create("1000000001"),
                Id.generate(),
                Money.create(new BigDecimal("1500.00"), "USD"));

        assertEquals(AccountStatus.OPEN, account.getStatus());
    }

    @Test
    @DisplayName("closing an open account marks it closed")
    void closingAnOpenAccountMarksItClosed() {
        Account account = openAccount();

        account.close();

        assertEquals(AccountStatus.CLOSED, account.getStatus());
    }

    @Test
    @DisplayName("closing an already closed account is rejected")
    void closingAnAlreadyClosedAccountIsRejected() {
        Account account = openAccount();
        account.close();

        DomainException exception = assertThrows(DomainException.class, account::close);

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("a closed account rejects a withdrawal")
    void aClosedAccountRejectsAWithdrawal() {
        Account account = openAccount();
        account.close();
        Money amount = Money.create(new BigDecimal("10.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        DomainException exception = assertThrows(
                DomainException.class,
                () -> account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("100.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("a closed account rejects a credit")
    void aClosedAccountRejectsACredit() {
        Account account = openAccount();
        account.close();

        DomainException exception = assertThrows(
                DomainException.class,
                () -> account.credit(Money.create(new BigDecimal("10.00"), "USD")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("100.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("a closed account rejects a credit reversal")
    void aClosedAccountRejectsACreditReversal() {
        Account account = openAccount();
        account.close();

        DomainException exception = assertThrows(
                DomainException.class,
                () -> account.reverseCredit(Money.create(new BigDecimal("10.00"), "USD")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("100.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("maintaining an open account replaces its account number")
    void maintainingAnOpenAccountReplacesItsAccountNumber() {
        Account account = openAccount();

        account.maintain(AccountNumber.create("2000000001"));

        assertEquals("2000000001", account.getAccountNumber().getValue());
        assertEquals(AccountStatus.OPEN, account.getStatus());
    }

    @Test
    @DisplayName("maintaining a closed account is rejected")
    void maintainingAClosedAccountIsRejected() {
        Account account = openAccount();
        account.close();

        DomainException exception = assertThrows(
                DomainException.class,
                () -> account.maintain(AccountNumber.create("2000000001")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals("1234567890", account.getAccountNumber().getValue());
    }

    private Account openAccount() {
        return Account.open(
                Id.generate(),
                AccountNumber.create("1234567890"),
                Id.generate(),
                Money.create(new BigDecimal("100.00"), "USD"));
    }
}
