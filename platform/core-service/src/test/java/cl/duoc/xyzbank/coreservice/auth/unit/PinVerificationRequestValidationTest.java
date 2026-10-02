package cl.duoc.xyzbank.coreservice.auth.unit;

import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationRequest;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.rest.PinVerificationController;
import org.springframework.http.HttpStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The PIN verification request")
class PinVerificationRequestValidationTest {

    /*
     * Cases:
     * 1. A blank card number is rejected
     * 2. A blank PIN is rejected
     * 3. A card number and a PIN are accepted
     */

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("rejects a blank card number")
    void rejectsABlankCardNumber() {
        PinVerificationRequest request = new PinVerificationRequest(" ", "1234");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("rejects a blank pin")
    void rejectsABlankPin() {
        PinVerificationRequest request = new PinVerificationRequest("77777777-7777-7777-7777-777777777777", "");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("accepts a card number and a pin")
    void acceptsACardNumberAndAPin() {
        PinVerificationRequest request = new PinVerificationRequest("77777777-7777-7777-7777-777777777777", "1234");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("uses the same generic detail for an incorrect pin and a locked card")
    void usesTheSameGenericDetailForAnIncorrectPinAndALockedCard() {
        String incorrect = PinVerificationController.pinFailure(HttpStatus.UNAUTHORIZED).getDetail();
        String locked = PinVerificationController.pinFailure(HttpStatus.LOCKED).getDetail();

        assertEquals(incorrect, locked);
        assertEquals("PIN verification failed", incorrect);
    }
}
