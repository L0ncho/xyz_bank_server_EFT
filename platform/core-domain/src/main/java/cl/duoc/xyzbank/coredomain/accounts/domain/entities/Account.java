package cl.duoc.xyzbank.coredomain.accounts.domain.entities;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.DailyWithdrawalUsage;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public final class Account {

    private final Id id;
    private final AccountNumber accountNumber;
    private final Id customerId;
    private Money balance;
    private final long version;
    private DailyWithdrawalUsage dailyWithdrawalUsage;

    private Account(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            DailyWithdrawalUsage dailyWithdrawalUsage) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.balance = balance;
        this.version = version;
        this.dailyWithdrawalUsage = dailyWithdrawalUsage;
    }

    public static Account create(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            DailyWithdrawalUsage dailyWithdrawalUsage) {
        return new Account(id, accountNumber, customerId, balance, version, dailyWithdrawalUsage);
    }

    public static Account create(Id id, AccountNumber accountNumber, Id customerId, Money balance) {
        return create(id, accountNumber, customerId, balance, 0L, DailyWithdrawalUsage.none(balance.getCurrency()));
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

    public void withdraw(Money amount, LocalDate today, Money dailyLimit) {
        Money newBalance = this.balance.subtract(amount);
        this.dailyWithdrawalUsage = this.dailyWithdrawalUsage.recordWithdrawal(amount, today, dailyLimit);
        this.balance = newBalance;
    }

    public void credit(Money amount) {
        this.balance = this.balance.add(amount);
    }

    public void reverseCredit(Money amount) {
        this.balance = this.balance.subtract(amount);
    }

    public Map<String, Object> toPrimitives() {
        return Map.of(
                "id", id.getValue(),
                "accountNumber", accountNumber.getValue(),
                "customerId", customerId.getValue(),
                "balance", balance.toPrimitives());
    }
}
