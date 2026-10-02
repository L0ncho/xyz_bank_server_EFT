package cl.duoc.xyzbank.bffatm.withdrawal.unit;

import cl.duoc.xyzbank.bffatm.auth.infrastructure.rest.dto.PinVerificationRequest;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The ATM request bodies")
class WithdrawalRequestValidationTest {

    /*
     * Cases:
     * 1. A withdrawal without an amount is rejected
     * 2. A withdrawal without a currency is rejected
     * 3. A PIN request without a card is rejected
     * 4. A complete withdrawal is accepted
     */

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("rejects a withdrawal without an amount")
    void rejectsAWithdrawalWithoutAnAmount() {
        WithdrawalRequest request = new WithdrawalRequest(null, "USD");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("rejects a withdrawal without a currency")
    void rejectsAWithdrawalWithoutACurrency() {
        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("40.00"), " ");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("rejects a pin request without a card")
    void rejectsAPinRequestWithoutACard() {
        PinVerificationRequest request = new PinVerificationRequest("", "1234");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("accepts a complete withdrawal")
    void acceptsACompleteWithdrawal() {
        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("40.00"), "USD");

        assertTrue(validator.validate(request).isEmpty());
    }
}
