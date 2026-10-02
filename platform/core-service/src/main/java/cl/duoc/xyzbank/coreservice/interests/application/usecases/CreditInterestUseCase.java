package cl.duoc.xyzbank.coreservice.interests.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestCreditRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.ProcessedInterestEventRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coreservice.interests.application.dto.CreditInterestRequest;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditRejected;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditReversed;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditResponse;
import cl.duoc.xyzbank.coreservice.interests.application.ports.InterestCreditResultPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

public class CreditInterestUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final InterestSummaryRepository interestSummaryRepository;
    private final InterestCreditRepository interestCreditRepository;
    private final ProcessedInterestEventRepository processedInterestEvents;
    private final InterestCreditResultPublisher resultPublisher;
    private final Clock clock;

    public CreditInterestUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository,
            InterestCreditRepository interestCreditRepository,
            Clock clock) {
        this(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                interestCreditRepository,
                new UntrackedInterestEvents(),
                clock);
    }

    public CreditInterestUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository,
            InterestCreditRepository interestCreditRepository,
            ProcessedInterestEventRepository processedInterestEvents,
            Clock clock) {
        this(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                interestCreditRepository,
                processedInterestEvents,
                clock,
                new UntrackedInterestCreditResults());
    }

    public CreditInterestUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository,
            InterestCreditRepository interestCreditRepository,
            Clock clock,
            InterestCreditResultPublisher resultPublisher) {
        this(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                interestCreditRepository,
                new UntrackedInterestEvents(),
                clock,
                resultPublisher);
    }

    public CreditInterestUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository,
            InterestCreditRepository interestCreditRepository,
            ProcessedInterestEventRepository processedInterestEvents,
            Clock clock,
            InterestCreditResultPublisher resultPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.interestSummaryRepository = interestSummaryRepository;
        this.interestCreditRepository = interestCreditRepository;
        this.processedInterestEvents = processedInterestEvents;
        this.clock = clock;
        this.resultPublisher = resultPublisher;
    }

    public void executeFromEvent(CreditInterestRequest request, String eventId) {
        try {
            execute(request, eventId);
        } catch (DomainException exception) {
            if (isBusinessRejection(exception)) {
                resultPublisher.reject(new InterestCreditRejected(
                        eventId, request.accountId(), exception.getMessage()));
                return;
            }
            throw exception;
        }
    }

    public InterestCreditResponse execute(CreditInterestRequest request, String eventId) {
        Optional<String> processedKey = processedInterestEvents.findIdempotencyKey(eventId);
        if (processedKey.isPresent()) {
            return execute(withIdempotencyKey(request, processedKey.get()));
        }
        InterestCreditResponse response = execute(request);
        processedInterestEvents.register(eventId, request.idempotencyKey());
        return response;
    }

    public InterestCreditResponse execute(CreditInterestRequest request) {
        requireIdempotencyKey(request);

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        requirePositiveAmount(request);

        Id accountId = Id.create(request.accountId());
        Account account = findAccountOrThrow(accountId);

        requireYearNotAlreadyCredited(accountId, request.year());

        Money amount = Money.create(request.amount(), request.currency());
        account.credit(amount);

        LocalDate today = LocalDate.now(clock);
        Transaction transaction = Transaction.create(
                Id.generate(),
                accountId,
                TransactionType.CREDIT,
                amount,
                today,
                null,
                Optional.of(request.idempotencyKey()));

        AnnualInterestSummary summary = AnnualInterestSummary.create(
                Id.generate(),
                accountId,
                request.year(),
                Money.create(request.openingBalance(), request.currency()),
                Money.create(request.closingBalance(), request.currency()),
                request.interestRate(),
                amount);

        interestCreditRepository.persistInterestCredit(account, transaction, summary);

        return toResponse(transaction, account.getBalance(), request.year());
    }

    private boolean isBusinessRejection(DomainException exception) {
        return exception.getType() == DomainException.Type.VALIDATION
                || exception.getType() == DomainException.Type.NOT_FOUND
                || exception.getType() == DomainException.Type.CONFLICT;
    }

    private CreditInterestRequest withIdempotencyKey(CreditInterestRequest request, String idempotencyKey) {
        return new CreditInterestRequest(
                request.accountId(),
                request.year(),
                request.amount(),
                request.currency(),
                request.interestRate(),
                request.openingBalance(),
                request.closingBalance(),
                idempotencyKey);
    }

    private void requireIdempotencyKey(CreditInterestRequest request) {
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw DomainException.validation("Idempotency key is required");
        }
    }

    private void requirePositiveAmount(CreditInterestRequest request) {
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw DomainException.validation("Amount must be positive");
        }
    }

    private void requireYearNotAlreadyCredited(Id accountId, int year) {
        if (interestSummaryRepository.findByAccountIdAndYear(accountId, year).isPresent()) {
            throw DomainException.conflict(
                    "Interest already credited for account " + accountId.getValue() + " and year " + year);
        }
    }

    private Account findAccountOrThrow(Id accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> DomainException.notFound("Account " + accountId.getValue() + " not found"));
    }

    private InterestCreditResponse replay(Transaction existing, CreditInterestRequest request) {
        boolean matchesOriginalRequest = existing.getAccountId().equals(Id.create(request.accountId()))
                && existing.getAmount().getAmount().compareTo(request.amount()) == 0
                && existing.getAmount().getCurrency().equals(request.currency());
        if (!matchesOriginalRequest) {
            throw DomainException.conflict("Idempotency key already used with different parameters");
        }

        Account account = findAccountOrThrow(existing.getAccountId());
        return toResponse(existing, account.getBalance(), request.year());
    }

    private InterestCreditResponse toResponse(Transaction transaction, Money newBalance, int year) {
        return new InterestCreditResponse(
                transaction.getId().getValue(),
                transaction.getAccountId().getValue(),
                year,
                transaction.getAmount().getAmount(),
                transaction.getAmount().getCurrency(),
                transaction.getOccurredOn().toString(),
                newBalance.getAmount());
    }

    private static final class UntrackedInterestEvents implements ProcessedInterestEventRepository {

        @Override
        public Optional<String> findIdempotencyKey(String eventId) {
            return Optional.empty();
        }

        @Override
        public void register(String eventId, String idempotencyKey) {
        }
    }

    private static final class UntrackedInterestCreditResults implements InterestCreditResultPublisher {

        @Override
        public void reject(InterestCreditRejected rejection) {
        }

        @Override
        public void reverse(InterestCreditReversed reversal) {
        }
    }
}
