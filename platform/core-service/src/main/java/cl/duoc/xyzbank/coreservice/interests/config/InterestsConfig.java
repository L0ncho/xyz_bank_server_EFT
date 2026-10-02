package cl.duoc.xyzbank.coreservice.interests.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestCreditRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.ProcessedInterestEventRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditRejected;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditReversed;
import cl.duoc.xyzbank.coreservice.interests.application.ports.InterestCreditResultPublisher;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.CreditInterestUseCase;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.GetAnnualInterestSummaryUseCase;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.ReverseInterestCreditUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class InterestsConfig {

    @Bean
    public GetAnnualInterestSummaryUseCase getAnnualInterestSummaryUseCase(
            InterestSummaryRepository interestSummaryRepository) {
        return new GetAnnualInterestSummaryUseCase(interestSummaryRepository);
    }

    @Bean
    public CreditInterestUseCase creditInterestUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository,
            InterestCreditRepository interestCreditRepository,
            ProcessedInterestEventRepository processedInterestEvents,
            Clock clock,
            InterestCreditResultPublisher resultPublisher) {
        return new CreditInterestUseCase(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                interestCreditRepository,
                processedInterestEvents,
                clock,
                resultPublisher);
    }

    @Bean
    public ReverseInterestCreditUseCase reverseInterestCreditUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository,
            InterestCreditRepository interestCreditRepository,
            InterestCreditResultPublisher resultPublisher,
            Clock clock) {
        return new ReverseInterestCreditUseCase(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                interestCreditRepository,
                resultPublisher,
                clock);
    }

    @Bean
    @ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "false", matchIfMissing = true)
    public InterestCreditResultPublisher noOpInterestCreditResultPublisher() {
        return new InterestCreditResultPublisher() {
            @Override
            public void reject(InterestCreditRejected rejection) {
            }

            @Override
            public void reverse(InterestCreditReversed reversal) {
            }
        };
    }
}
