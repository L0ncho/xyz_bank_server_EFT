package cl.duoc.xyzbank.coredomain.payments.domain.repositories;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;

public interface PaymentLedger {

    void recordDeposit(Account account, Transaction transaction);

    void recordTransfer(Account source, Transaction debit, Account destination, Transaction credit);

    void recordExternalPayment(Account account, Transaction transaction);
}
