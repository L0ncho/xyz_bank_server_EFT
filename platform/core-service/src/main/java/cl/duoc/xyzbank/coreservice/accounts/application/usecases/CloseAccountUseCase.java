package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;

public class CloseAccountUseCase {

    private final AccountRepository accountRepository;

    public CloseAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse execute(String accountId) {
        Id id = Id.create(accountId);
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Account " + accountId + " not found"));
        account.close();
        accountRepository.save(account);
        return AccountResponse.from(account);
    }
}
