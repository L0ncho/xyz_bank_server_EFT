package cl.duoc.xyzbank.coreservice.interests.unit;

import cl.duoc.xyzbank.coreservice.interests.infrastructure.rest.InterestCreditController.InterestCreditBody;
import cl.duoc.xyzbank.coreservice.withdrawals.infrastructure.rest.WithdrawalController.WithdrawBody;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The interest credit request")
class InterestCreditBodyValidationTest {

    /*
     * Cases:
     * 1. A missing amount is rejected
     * 2. A blank currency is rejected
     * 3. A non-positive year is rejected
     * 4. A zero amount stays valid here so the domain can still answer 422
     * 5. A complete positive body is accepted
     */

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("rejects a missing amount")
    void rejectsAMissingAmount() {
        InterestCreditBody body = new InterestCreditBody(2025, null, "USD", decimal("0.035"), decimal("1000"), decimal("1035"));

        assertFalse(validator.validate(body).isEmpty());
    }

    @Test
    @DisplayName("rejects a blank currency")
    void rejectsABlankCurrency() {
        InterestCreditBody body = new InterestCreditBody(2025, decimal("35"), " ", decimal("0.035"), decimal("1000"), decimal("1035"));

        assertFalse(validator.validate(body).isEmpty());
    }

    @Test
    @DisplayName("rejects a non-positive year")
    void rejectsANonPositiveYear() {
        InterestCreditBody body = new InterestCreditBody(0, decimal("35"), "USD", decimal("0.035"), decimal("1000"), decimal("1035"));

        assertFalse(validator.validate(body).isEmpty());
    }

    @Test
    @DisplayName("accepts a zero amount so the domain can reject it as unprocessable")
    void acceptsAZeroAmountSoTheDomainCanRejectIt() {
        InterestCreditBody body = new InterestCreditBody(2025, decimal("0"), "USD", decimal("0.035"), decimal("1000"), decimal("1000"));

        assertTrue(validator.validate(body).isEmpty());
    }

    @Test
    @DisplayName("accepts a complete interest credit body")
    void acceptsACompleteInterestCreditBody() {
        InterestCreditBody body = new InterestCreditBody(2025, decimal("35"), "USD", decimal("0.035"), decimal("1000"), decimal("1035"));

        assertTrue(validator.validate(body).isEmpty());
    }

    @Test
    @DisplayName("rejects a withdrawal with a missing amount")
    void rejectsAWithdrawalWithAMissingAmount() {
        WithdrawBody body = new WithdrawBody(null, "USD");

        assertFalse(validator.validate(body).isEmpty());
    }

    @Test
    @DisplayName("rejects a withdrawal with a blank currency")
    void rejectsAWithdrawalWithABlankCurrency() {
        WithdrawBody body = new WithdrawBody(decimal("10"), "");

        assertFalse(validator.validate(body).isEmpty());
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
