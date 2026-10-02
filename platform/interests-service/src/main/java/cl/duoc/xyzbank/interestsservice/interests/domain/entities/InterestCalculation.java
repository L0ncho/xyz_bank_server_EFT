package cl.duoc.xyzbank.interestsservice.interests.domain.entities;

import java.math.BigDecimal;

public final class InterestCalculation {

    private final String eventId;
    private final String accountId;
    private final int period;
    private final BigDecimal amount;
    private final String currency;
    private InterestCalculationStatus status;
    private String reason;

    private InterestCalculation(
            String eventId,
            String accountId,
            int period,
            BigDecimal amount,
            String currency,
            InterestCalculationStatus status,
            String reason) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.period = period;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.reason = reason;
    }

    public static InterestCalculation pending(
            String eventId, String accountId, int period, BigDecimal amount, String currency) {
        return new InterestCalculation(
                eventId, accountId, period, amount, currency, InterestCalculationStatus.PENDING, null);
    }

    public void apply() {
        if (isClosed()) {
            return;
        }
        this.status = InterestCalculationStatus.APPLIED;
    }

    public void reject(String reason) {
        if (isClosed()) {
            return;
        }
        this.status = InterestCalculationStatus.REJECTED;
        this.reason = reason;
    }

    public void reverse() {
        if (status != InterestCalculationStatus.APPLIED) {
            return;
        }
        this.status = InterestCalculationStatus.REVERSED;
    }

    public boolean isClosed() {
        return status != InterestCalculationStatus.PENDING;
    }

    public String eventId() {
        return eventId;
    }

    public String accountId() {
        return accountId;
    }

    public int period() {
        return period;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public InterestCalculationStatus status() {
        return status;
    }

    public String reason() {
        return reason;
    }
}
