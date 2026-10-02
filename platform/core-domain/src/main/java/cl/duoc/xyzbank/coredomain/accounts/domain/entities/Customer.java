package cl.duoc.xyzbank.coredomain.accounts.domain.entities;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.Map;

public final class Customer {

    private final Id id;
    private String fullName;
    private String email;

    private Customer(Id id, String fullName, String email) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
    }

    public static Customer create(Id id, String fullName, String email) {
        if (fullName == null || fullName.isBlank()) {
            throw DomainException.validation("Full name cannot be empty");
        }
        if (email == null || !email.contains("@")) {
            throw DomainException.validation("Invalid email format");
        }
        return new Customer(id, fullName, email);
    }

    public Id getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public void updateProfile(String fullName, String email) {
        Customer validated = create(id, fullName, email);
        this.fullName = validated.fullName;
        this.email = validated.email;
    }

    public Map<String, Object> toPrimitives() {
        return Map.of(
                "id", id.getValue(),
                "fullName", fullName,
                "email", email);
    }
}
