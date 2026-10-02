package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("A transfer between two accounts")
class AccountTransferTest {

    private int accountSequence = 1;

    /*
     * Cases:
     * 1. Moves the amount from the source to the destination
     * 2. Does not debit the source when the destination is closed
     * 3. Does not credit the destination when the source cannot cover the amount
     */

    @Test
    @DisplayName("moves the amount from the source to the destination")
    void movesTheAmountFromTheSourceToTheDestination() {
        Account source = anAccount("500.00", "USD");
        Account destination = anAccount("200.00", "USD");

        source.transferTo(destination, Money.create(new BigDecimal("100.00"), "USD"));

        assertEquals(new BigDecimal("400.00"), source.getBalance().getAmount());
        assertEquals(new BigDecimal("300.00"), destination.getBalance().getAmount());
        assertEquals(0, source.getDailyWithdrawnAmount().getAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("does not debit the source when the destination is closed")
    void doesNotDebitTheSourceWhenTheDestinationIsClosed() {
        Account source = anAccount("500.00", "USD");
        Account destination = anAccount("200.00", "USD");
        destination.close();

        DomainException exception = assertThrows(
                DomainException.class,
                () -> source.transferTo(destination, Money.create(new BigDecimal("100.00"), "USD")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("500.00"), source.getBalance().getAmount());
        assertEquals(new BigDecimal("200.00"), destination.getBalance().getAmount());
    }

    @Test
    @DisplayName("does not credit the destination when the source cannot cover the amount")
    void doesNotCreditTheDestinationWhenTheSourceCannotCoverTheAmount() {
        Account source = anAccount("50.00", "USD");
        Account destination = anAccount("200.00", "USD");

        DomainException exception = assertThrows(
                DomainException.class,
                () -> source.transferTo(destination, Money.create(new BigDecimal("100.00"), "USD")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("50.00"), source.getBalance().getAmount());
        assertEquals(new BigDecimal("200.00"), destination.getBalance().getAmount());
    }

    private Account anAccount(String balance, String currency) {
        String accountNumber = String.valueOf(1_000_000_000L + accountSequence++);
        return Account.create(
                Id.generate(),
                AccountNumber.create(accountNumber),
                Id.generate(),
                Money.create(new BigDecimal(balance), currency));
    }
}
