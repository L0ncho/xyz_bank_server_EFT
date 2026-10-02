package cl.duoc.xyzbank.coreservice.interests.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestCreditRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;

public class InMemoryInterestCreditRepository implements InterestCreditRepository {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final InterestSummaryRepository interestSummaryRepository;

    public InMemoryInterestCreditRepository(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.interestSummaryRepository = interestSummaryRepository;
    }

    @Override
    public void persistInterestCredit(Account account, Transaction transaction, AnnualInterestSummary summary) {
        if (interestSummaryRepository.findByAccountIdAndYear(summary.getAccountId(), summary.getYear()).isPresent()) {
            throw DomainException.conflict("Interest already credited for this account and year");
        }
        accountRepository.save(account);
        transactionRepository.save(transaction);
        interestSummaryRepository.save(summary);
    }

    @Override
    public void persistInterestReversal(Account account, Transaction transaction) {
        accountRepository.save(account);
        transactionRepository.save(transaction);
    }
}
