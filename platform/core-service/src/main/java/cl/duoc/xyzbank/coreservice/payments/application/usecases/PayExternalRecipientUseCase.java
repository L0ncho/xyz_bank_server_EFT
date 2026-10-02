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
import cl.duoc.xyzbank.coreservice.payments.application.dto.ExternalPaymentRequest;
import cl.duoc.xyzbank.coreservice.payments.application.dto.ExternalPaymentResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

public class PayExternalRecipientUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentLedger paymentLedger;
    private final Clock clock;

    public PayExternalRecipientUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PaymentLedger paymentLedger,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.paymentLedger = paymentLedger;
        this.clock = clock;
    }

    public ExternalPaymentResponse execute(ExternalPaymentRequest request) {
        requireIdempotencyKey(request.idempotencyKey());

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        requirePositiveAmount(request.amount());
        requireRecipient(request.recipient());

        Id accountId = Id.create(request.accountId());
        Account account = findAccountOrThrow(accountId);
        Money amount = Money.create(request.amount(), request.currency());
        account.debit(amount);

        Transaction transaction = Transaction.create(
                Id.generate(),
                accountId,
                TransactionType.DEBIT,
                amount,
                LocalDate.now(clock),
                request.recipient(),
                Optional.of(request.idempotencyKey()));
        paymentLedger.recordExternalPayment(account, transaction);
        return toResponse(transaction, account.getBalance(), request.recipient());
    }

    private ExternalPaymentResponse replay(Transaction existing, ExternalPaymentRequest request) {
        boolean matchesOriginalRequest = existing.getType() == TransactionType.DEBIT
                && existing.getAccountId().equals(Id.create(request.accountId()))
                && existing.getAmount().getAmount().compareTo(request.amount()) == 0
                && existing.getAmount().getCurrency().equals(request.currency())
                && request.recipient().equals(existing.getDescription());
        if (!matchesOriginalRequest) {
            throw DomainException.conflict("Idempotency key already used with different parameters");
        }

        Account account = findAccountOrThrow(existing.getAccountId());
        return toResponse(existing, account.getBalance(), existing.getDescription());
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

    private void requireRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw DomainException.validation("Recipient is required");
        }
    }

    private Account findAccountOrThrow(Id accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> DomainException.notFound("Account " + accountId.getValue() + " not found"));
    }

    private ExternalPaymentResponse toResponse(Transaction transaction, Money newBalance, String recipient) {
        return new ExternalPaymentResponse(
                transaction.getId().getValue(),
                transaction.getAccountId().getValue(),
                recipient,
                transaction.getAmount().getAmount(),
                transaction.getAmount().getCurrency(),
                transaction.getOccurredOn().toString(),
                newBalance.getAmount());
    }
}
