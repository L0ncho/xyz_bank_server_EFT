package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.MaintainAccountRequest;

import java.util.Optional;

public class MaintainAccountUseCase {

    private final AccountRepository accountRepository;

    public MaintainAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse execute(MaintainAccountRequest request) {
        Id accountId = Id.create(request.accountId());
        AccountNumber accountNumber = AccountNumber.create(request.accountNumber());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> DomainException.notFound("Account " + request.accountId() + " not found"));
        rejectDuplicateAccountNumber(account, accountNumber);
        account.maintain(accountNumber);
        accountRepository.save(account);
        return AccountResponse.from(account);
    }

    private void rejectDuplicateAccountNumber(Account account, AccountNumber accountNumber) {
        Optional<Account> existing = accountRepository.findByAccountNumber(accountNumber);
        if (existing.isPresent() && !existing.get().getId().equals(account.getId())) {
            throw DomainException.conflict("Account number " + accountNumber.getValue() + " already exists");
        }
    }
}
