package cl.duoc.xyzbank.coreservice.payments.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.payments.domain.repositories.PaymentLedger;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaPaymentLedger implements PaymentLedger {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public JpaPaymentLedger(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public void recordDeposit(Account account, Transaction transaction) {
        accountRepository.save(account);
        transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void recordTransfer(
            Account source, Transaction debit, Account destination, Transaction credit) {
        accountRepository.save(source);
        accountRepository.save(destination);
        transactionRepository.save(debit);
        transactionRepository.save(credit);
    }

    @Override
    @Transactional
    public void recordExternalPayment(Account account, Transaction transaction) {
        accountRepository.save(account);
        transactionRepository.save(transaction);
    }
}
