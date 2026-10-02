package cl.duoc.xyzbank.coredomain.interests.domain.repositories;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;

public interface InterestCreditRepository {
    void persistInterestCredit(Account account, Transaction transaction, AnnualInterestSummary summary);

    void persistInterestReversal(Account account, Transaction transaction);
}
