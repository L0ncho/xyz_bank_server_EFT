package cl.duoc.xyzbank.coreservice.payments.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.payments.domain.repositories.PaymentLedger;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coreservice.payments.application.dto.TransferRequest;
import cl.duoc.xyzbank.coreservice.payments.application.dto.TransferResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

public class TransferBetweenAccountsUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentLedger paymentLedger;
    private final Clock clock;

    public TransferBetweenAccountsUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PaymentLedger paymentLedger,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.paymentLedger = paymentLedger;
        this.clock = clock;
    }

    public TransferResponse execute(TransferRequest request) {
        requireIdempotencyKey(request.idempotencyKey());

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        requirePositiveAmount(request.amount());

        Account source = findAccountOrThrow(Id.create(request.sourceAccountId()));
        Account destination = findAccountOrThrow(Id.create(request.destinationAccountId()));
        Money amount = Money.create(request.amount(), request.currency());
        source.transferTo(destination, amount);

        LocalDate today = LocalDate.now(clock);
        Transaction debit = Transaction.create(
                Id.generate(),
                source.getId(),
                TransactionType.DEBIT,
                amount,
                today,
                request.destinationAccountId(),
                Optional.of(request.idempotencyKey()));
        Transaction credit = Transaction.create(
                Id.generate(),
                destination.getId(),
                TransactionType.CREDIT,
                amount,
                today,
                request.sourceAccountId(),
                Optional.empty());
        paymentLedger.recordTransfer(source, debit, destination, credit);
        return toResponse(debit, source, destination);
    }

    private TransferResponse replay(Transaction existing, TransferRequest request) {
        boolean matchesOriginalRequest = existing.getType() == TransactionType.DEBIT
                && existing.getAccountId().equals(Id.create(request.sourceAccountId()))
                && existing.getAmount().getAmount().compareTo(request.amount()) == 0
                && existing.getAmount().getCurrency().equals(request.currency())
                && request.destinationAccountId().equals(existing.getDescription());
        if (!matchesOriginalRequest) {
            throw DomainException.conflict("Idempotency key already used with different parameters");
        }

        Account source = findAccountOrThrow(existing.getAccountId());
        Account destination = findAccountOrThrow(Id.create(existing.getDescription()));
        return toResponse(existing, source, destination);
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw DomainException.validation("Idempotency key is required");
        }
    }

    private void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw DomainException.validation("Amount must be positive");
        }
    }

    private Account findAccountOrThrow(Id accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> DomainException.notFound("Account " + accountId.getValue() + " not found"));
    }

    private TransferResponse toResponse(Transaction debit, Account source, Account destination) {
        return new TransferResponse(
                debit.getId().getValue(),
                source.getId().getValue(),
                destination.getId().getValue(),
                debit.getAmount().getAmount(),
                debit.getAmount().getCurrency(),
                debit.getOccurredOn().toString(),
                source.getBalance().getAmount(),
                destination.getBalance().getAmount());
    }
}
