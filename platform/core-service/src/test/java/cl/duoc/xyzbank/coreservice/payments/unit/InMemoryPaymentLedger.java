package cl.duoc.xyzbank.coreservice.payments.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.payments.domain.repositories.PaymentLedger;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;

public class InMemoryPaymentLedger implements PaymentLedger {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public InMemoryPaymentLedger(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void recordDeposit(Account account, Transaction transaction) {
        accountRepository.save(account);
        transactionRepository.save(transaction);
    }

    @Override
    public void recordTransfer(Account source, Transaction debit, Account destination, Transaction credit) {
        accountRepository.save(source);
        accountRepository.save(destination);
        transactionRepository.save(debit);
        transactionRepository.save(credit);
    }

    @Override
    public void recordExternalPayment(Account account, Transaction transaction) {
        accountRepository.save(account);
        transactionRepository.save(transaction);
    }
}
