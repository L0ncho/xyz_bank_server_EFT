package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.CustomerProfileResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.UpdateCustomerProfileRequest;

public class UpdateCustomerProfileUseCase {

    private final CustomerRepository customerRepository;

    public UpdateCustomerProfileUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerProfileResponse execute(UpdateCustomerProfileRequest request) {
        Id id = Id.create(request.customerId());
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Customer " + request.customerId() + " not found"));
        customer.updateProfile(request.fullName(), request.email());
        customerRepository.save(customer);
        return toResponse(customer);
    }

    private CustomerProfileResponse toResponse(Customer customer) {
        return new CustomerProfileResponse(
                customer.getId().getValue(),
                customer.getFullName(),
                customer.getEmail());
    }
}
