package cl.duoc.xyzbank.coreservice.accounts.application.dto;

public record UpdateCustomerProfileRequest(String customerId, String fullName, String email) {
}
