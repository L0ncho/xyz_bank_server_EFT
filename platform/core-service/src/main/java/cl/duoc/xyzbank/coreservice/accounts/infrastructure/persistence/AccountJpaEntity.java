package cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountJpaEntity {

    @Id
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private String currency;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "daily_withdrawn_amount", nullable = false)
    private BigDecimal dailyWithdrawnAmount;

    @Column(name = "daily_withdrawn_date")
    private LocalDate dailyWithdrawnDate;

    @Column(nullable = false)
    private String status;

    protected AccountJpaEntity() {
    }

    public AccountJpaEntity(
            UUID id,
            String accountNumber,
            UUID customerId,
            BigDecimal balance,
            String currency,
            long version,
            BigDecimal dailyWithdrawnAmount,
            LocalDate dailyWithdrawnDate,
            String status) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.balance = balance;
        this.currency = currency;
        this.version = version;
        this.dailyWithdrawnAmount = dailyWithdrawnAmount;
        this.dailyWithdrawnDate = dailyWithdrawnDate;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public long getVersion() {
        return version;
    }

    public BigDecimal getDailyWithdrawnAmount() {
        return dailyWithdrawnAmount;
    }

    public LocalDate getDailyWithdrawnDate() {
        return dailyWithdrawnDate;
    }

    public String getStatus() {
        return status;
    }
}
