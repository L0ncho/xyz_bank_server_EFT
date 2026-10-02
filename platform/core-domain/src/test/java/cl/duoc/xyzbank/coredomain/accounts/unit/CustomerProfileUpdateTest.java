package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The customer profile update")
class CustomerProfileUpdateTest {

    /*
     * Cases:
     * 1. Updates the full name and email
     * 2. Rejects a blank full name and keeps the current profile
     * 3. Rejects an invalid email and keeps the current profile
     */

    @Test
    @DisplayName("updates the full name and email")
    void updatesTheFullNameAndEmail() {
        Customer customer = Customer.create(Id.generate(), "Jane Doe", "jane.doe@xyzbank.cl");

        customer.updateProfile("Ana Soto", "ana.soto@xyzbank.cl");

        assertEquals("Ana Soto", customer.getFullName());
        assertEquals("ana.soto@xyzbank.cl", customer.getEmail());
    }

    @Test
    @DisplayName("rejects a blank full name and keeps the current profile")
    void rejectsABlankFullNameAndKeepsTheCurrentProfile() {
        Customer customer = Customer.create(Id.generate(), "Jane Doe", "jane.doe@xyzbank.cl");

        DomainException exception = assertThrows(
                DomainException.class, () -> customer.updateProfile("  ", "ana.soto@xyzbank.cl"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals("Jane Doe", customer.getFullName());
        assertEquals("jane.doe@xyzbank.cl", customer.getEmail());
    }

    @Test
    @DisplayName("rejects an invalid email and keeps the current profile")
    void rejectsAnInvalidEmailAndKeepsTheCurrentProfile() {
        Customer customer = Customer.create(Id.generate(), "Jane Doe", "jane.doe@xyzbank.cl");

        DomainException exception = assertThrows(
                DomainException.class, () -> customer.updateProfile("Ana Soto", "not-an-email"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals("Jane Doe", customer.getFullName());
        assertEquals("jane.doe@xyzbank.cl", customer.getEmail());
    }
}
