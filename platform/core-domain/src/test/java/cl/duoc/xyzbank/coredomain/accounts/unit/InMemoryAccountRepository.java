package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public InMemoryAccountRepository() {
    }

    public InMemoryAccountRepository(List<Account> initialAccounts) {
        initialAccounts.forEach(this::save);
    }

    @Override
    public void save(Account account) {
        accounts.put(account.getId().getValue(), account);
    }

    @Override
    public Optional<Account> findById(Id id) {
        return Optional.ofNullable(accounts.get(id.getValue()));
    }

    @Override
    public Optional<Account> findByAccountNumber(AccountNumber accountNumber) {
        return accounts.values().stream()
                .filter(account -> account.getAccountNumber().equals(accountNumber))
                .findFirst();
    }

    @Override
    public List<Account> findByCustomerId(Id customerId) {
        return accounts.values().stream()
                .filter(account -> account.getCustomerId().equals(customerId))
                .toList();
    }
}
