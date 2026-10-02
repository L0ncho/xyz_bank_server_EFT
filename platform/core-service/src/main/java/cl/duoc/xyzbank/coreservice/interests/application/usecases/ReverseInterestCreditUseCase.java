package cl.duoc.xyzbank.coreservice.interests.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestCreditRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditResponse;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditReversed;
import cl.duoc.xyzbank.coreservice.interests.application.ports.InterestCreditResultPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

public class ReverseInterestCreditUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final InterestSummaryRepository interestSummaryRepository;
    private final InterestCreditRepository interestCreditRepository;
    private final InterestCreditResultPublisher resultPublisher;
    private final Clock clock;

    public ReverseInterestCreditUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository,
            InterestCreditRepository interestCreditRepository,
            InterestCreditResultPublisher resultPublisher,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.interestSummaryRepository = interestSummaryRepository;
        this.interestCreditRepository = interestCreditRepository;
        this.resultPublisher = resultPublisher;
        this.clock = clock;
    }

    public InterestCreditResponse execute(String accountId, int year) {
        Id id = Id.create(accountId);
        String reversalEventId = "interest:" + accountId + ":" + year + ":reversed";
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(reversalEventId);
        if (existing.isPresent()) {
            Account account = findAccount(id);
            return response(existing.get(), year, account.getBalance().getAmount());
        }

        AnnualInterestSummary summary = interestSummaryRepository.findByAccountIdAndYear(id, year)
                .orElseThrow(() -> DomainException.notFound(
                        "Interest credit for " + accountId + " in " + year + " is not applied"));
        Account account = findAccount(id);
        Money amount = summary.getInterestAmount();
        account.reverseCredit(amount);

        Transaction transaction = Transaction.create(
                Id.generate(),
                id,
                TransactionType.DEBIT,
                amount,
                LocalDate.now(clock),
                null,
                Optional.of(reversalEventId));
        interestCreditRepository.persistInterestReversal(account, transaction);
        resultPublisher.reverse(new InterestCreditReversed(
                reversalEventId,
                accountId,
                year,
                amount.getAmount(),
                amount.getCurrency()));
        return response(transaction, year, account.getBalance().getAmount());
    }

    private Account findAccount(Id accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> DomainException.notFound("Account " + accountId.getValue() + " not found"));
    }

    private InterestCreditResponse response(Transaction transaction, int year, java.math.BigDecimal newBalance) {
        return new InterestCreditResponse(
                transaction.getId().getValue(),
                transaction.getAccountId().getValue(),
                year,
                transaction.getAmount().getAmount(),
                transaction.getAmount().getCurrency(),
                transaction.getOccurredOn().toString(),
                newBalance);
    }
}
