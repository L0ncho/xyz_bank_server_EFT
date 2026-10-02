package cl.duoc.xyzbank.coredomain.accounts.domain.entities;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountStatus;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.DailyWithdrawalUsage;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public final class Account {

    private final Id id;
    private AccountNumber accountNumber;
    private final Id customerId;
    private Money balance;
    private final long version;
    private DailyWithdrawalUsage dailyWithdrawalUsage;
    private AccountStatus status;

    private Account(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            DailyWithdrawalUsage dailyWithdrawalUsage,
            AccountStatus status) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.balance = balance;
        this.version = version;
        this.dailyWithdrawalUsage = dailyWithdrawalUsage;
        this.status = status;
    }

    public static Account create(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            DailyWithdrawalUsage dailyWithdrawalUsage,
            AccountStatus status) {
        if (status == null) {
            throw DomainException.validation("Account status is required");
        }
        return new Account(id, accountNumber, customerId, balance, version, dailyWithdrawalUsage, status);
    }

    public static Account create(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            DailyWithdrawalUsage dailyWithdrawalUsage) {
        return create(
                id, accountNumber, customerId, balance, version, dailyWithdrawalUsage, AccountStatus.OPEN);
    }

    public static Account create(Id id, AccountNumber accountNumber, Id customerId, Money balance) {
        return create(id, accountNumber, customerId, balance, 0L, DailyWithdrawalUsage.none(balance.getCurrency()));
    }

    public static Account open(Id id, AccountNumber accountNumber, Id customerId, Money openingBalance) {
        return create(id, accountNumber, customerId, openingBalance);
    }

    public Id getId() {
        return id;
    }

    public AccountNumber getAccountNumber() {
        return accountNumber;
    }

    public Id getCustomerId() {
        return customerId;
    }

    public Money getBalance() {
        return balance;
    }

    public long getVersion() {
        return version;
    }

    public Money getDailyWithdrawnAmount() {
        return dailyWithdrawalUsage.getWithdrawnAmount();
    }

    public Optional<LocalDate> getDailyWithdrawnDate() {
        return dailyWithdrawalUsage.getDate();
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void close() {
        if (status == AccountStatus.CLOSED) {
            throw DomainException.validation("Account is already closed");
        }
        this.status = AccountStatus.CLOSED;
    }

    public void maintain(AccountNumber accountNumber) {
        if (status == AccountStatus.CLOSED) {
            throw DomainException.validation("Closed account cannot be maintained");
        }
        this.accountNumber = accountNumber;
    }

    public void withdraw(Money amount, LocalDate today, Money dailyLimit) {
        requireOpenForMovements();
        Money newBalance = this.balance.subtract(amount);
        this.dailyWithdrawalUsage = this.dailyWithdrawalUsage.recordWithdrawal(amount, today, dailyLimit);
        this.balance = newBalance;
    }

    public void credit(Money amount) {
        requireOpenForMovements();
        this.balance = this.balance.add(amount);
    }

    public void debit(Money amount) {
        ensureCanDebit(amount);
        this.balance = this.balance.subtract(amount);
    }

    public void transferTo(Account destination, Money amount) {
        if (id.equals(destination.getId())) {
            throw DomainException.validation("Cannot transfer to the same account");
        }
        destination.ensureCanCredit(amount);
        ensureCanDebit(amount);
        debit(amount);
        destination.credit(amount);
    }

    public void reverseCredit(Money amount) {
        requireOpenForMovements();
        this.balance = this.balance.subtract(amount);
    }

    private void requireOpenForMovements() {
        if (status == AccountStatus.CLOSED) {
            throw DomainException.validation("Closed account accepts no movements");
        }
    }

    private void ensureCanCredit(Money amount) {
        requireOpenForMovements();
        this.balance.add(amount);
    }

    private void ensureCanDebit(Money amount) {
        requireOpenForMovements();
        this.balance.subtract(amount);
    }

    public Map<String, Object> toPrimitives() {
        return Map.of(
                "id", id.getValue(),
                "accountNumber", accountNumber.getValue(),
                "customerId", customerId.getValue(),
                "balance", balance.toPrimitives(),
                "status", status.name());
    }
}
