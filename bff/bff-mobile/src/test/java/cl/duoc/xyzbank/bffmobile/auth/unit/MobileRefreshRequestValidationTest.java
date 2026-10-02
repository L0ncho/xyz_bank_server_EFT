package cl.duoc.xyzbank.bffmobile.auth.unit;

import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.dto.MobileRefreshRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The mobile refresh request")
class MobileRefreshRequestValidationTest {

    /*
     * Cases:
     * 1. A missing device is rejected
     * 2. A missing refresh token is rejected
     * 3. A device and a refresh token are accepted
     */

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("rejects a missing device")
    void rejectsAMissingDevice() {
        MobileRefreshRequest request = new MobileRefreshRequest(" ", "old-refresh-token");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("rejects a missing refresh token")
    void rejectsAMissingRefreshToken() {
        MobileRefreshRequest request = new MobileRefreshRequest("device-1", "");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("accepts a device and a refresh token")
    void acceptsADeviceAndARefreshToken() {
        MobileRefreshRequest request = new MobileRefreshRequest("device-1", "old-refresh-token");

        assertTrue(validator.validate(request).isEmpty());
    }
}
