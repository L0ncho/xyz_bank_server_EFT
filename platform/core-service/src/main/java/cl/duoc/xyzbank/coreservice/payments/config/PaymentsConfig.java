package cl.duoc.xyzbank.coreservice.payments.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.payments.domain.repositories.PaymentLedger;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.payments.application.usecases.DepositAccountUseCase;
import cl.duoc.xyzbank.coreservice.payments.application.usecases.PayExternalRecipientUseCase;
import cl.duoc.xyzbank.coreservice.payments.application.usecases.TransferBetweenAccountsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PaymentsConfig {

    @Bean
    public DepositAccountUseCase depositAccountUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PaymentLedger paymentLedger,
            Clock clock) {
        return new DepositAccountUseCase(accountRepository, transactionRepository, paymentLedger, clock);
    }

    @Bean
    public TransferBetweenAccountsUseCase transferBetweenAccountsUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PaymentLedger paymentLedger,
            Clock clock) {
        return new TransferBetweenAccountsUseCase(accountRepository, transactionRepository, paymentLedger, clock);
    }

    @Bean
    public PayExternalRecipientUseCase payExternalRecipientUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PaymentLedger paymentLedger,
            Clock clock) {
        return new PayExternalRecipientUseCase(accountRepository, transactionRepository, paymentLedger, clock);
    }
}
