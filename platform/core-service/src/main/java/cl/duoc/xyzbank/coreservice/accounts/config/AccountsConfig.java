package cl.duoc.xyzbank.coreservice.accounts.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.CloseAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetAccountBalanceUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetCustomerProfileUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.ListAccountsForCustomerUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.MaintainAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.OpenAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.UpdateCustomerProfileUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountsConfig {

    @Bean
    public GetCustomerProfileUseCase getCustomerProfileUseCase(CustomerRepository customerRepository) {
        return new GetCustomerProfileUseCase(customerRepository);
    }

    @Bean
    public UpdateCustomerProfileUseCase updateCustomerProfileUseCase(CustomerRepository customerRepository) {
        return new UpdateCustomerProfileUseCase(customerRepository);
    }

    @Bean
    public ListAccountsForCustomerUseCase listAccountsForCustomerUseCase(
            CustomerRepository customerRepository, AccountRepository accountRepository) {
        return new ListAccountsForCustomerUseCase(customerRepository, accountRepository);
    }

    @Bean
    public GetAccountBalanceUseCase getAccountBalanceUseCase(AccountRepository accountRepository) {
        return new GetAccountBalanceUseCase(accountRepository);
    }

    @Bean
    public OpenAccountUseCase openAccountUseCase(
            CustomerRepository customerRepository, AccountRepository accountRepository) {
        return new OpenAccountUseCase(customerRepository, accountRepository);
    }

    @Bean
    public CloseAccountUseCase closeAccountUseCase(AccountRepository accountRepository) {
        return new CloseAccountUseCase(accountRepository);
    }

    @Bean
    public MaintainAccountUseCase maintainAccountUseCase(AccountRepository accountRepository) {
        return new MaintainAccountUseCase(accountRepository);
    }
}
