package cl.duoc.xyzbank.coredomain.accounts.domain.repositories;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    void save(Account account);

    Optional<Account> findById(Id id);

    Optional<Account> findByAccountNumber(AccountNumber accountNumber);

    List<Account> findByCustomerId(Id customerId);
}
