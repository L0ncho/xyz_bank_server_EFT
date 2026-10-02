package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.OpenAccountRequest;

public class OpenAccountUseCase {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public OpenAccountUseCase(CustomerRepository customerRepository, AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    public AccountResponse execute(OpenAccountRequest request) {
        AccountNumber accountNumber = AccountNumber.create(request.accountNumber());
        Id customerId = Id.create(request.customerId());
        Money balance = Money.create(request.balance(), request.currency());
        customerRepository.findById(customerId)
                .orElseThrow(() -> DomainException.notFound("Customer " + request.customerId() + " not found"));
        if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            throw DomainException.conflict("Account number " + accountNumber.getValue() + " already exists");
        }

        Account account = Account.open(Id.generate(), accountNumber, customerId, balance);
        accountRepository.save(account);
        return AccountResponse.from(account);
    }
}
