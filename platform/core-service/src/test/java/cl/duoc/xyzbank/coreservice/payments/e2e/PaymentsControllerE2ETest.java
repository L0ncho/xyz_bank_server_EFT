package cl.duoc.xyzbank.coreservice.payments.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The payments controller")
class PaymentsControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. A deposit returns 201 and credits the account
     * 2. An amount of 0.00 stays a domain 422 and does not credit
     * 3. A transfer moves money, and the same idempotency key does not move it again
     * 4. A transfer to a closed account returns 422 and does not debit the source
     * 5. An external payment debits the payer only
     */

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JwtCallerContextAdapter tokenAdapter;

    private Id ownerId;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        ownerId = Id.generate();
        customerRepository.save(Customer.create(ownerId, "Jane Doe", "jane.doe+" + ownerId.getValue() + "@xyzbank.cl"));
    }

    @Test
    @DisplayName("returns 201 and credits the account")
    void returns201AndCreditsTheAccount() {
        Id accountId = anExistingAccount("500.00");

        asCustomer()
                .header("Idempotency-Key", "e2e-deposit-1")
                .contentType("application/json")
                .body(Map.of("amount", 100.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/deposits", accountId.getValue())
                .then()
                .statusCode(201)
                .body("newBalance", equalTo(600.00f))
                .body("accountId", equalTo(accountId.getValue()));

        assertEquals(new BigDecimal("600.00"), balanceOf(accountId));
    }

    @Test
    @DisplayName("returns 422 for an amount of zero and does not credit the account")
    void returns422ForAnAmountOfZeroAndDoesNotCreditTheAccount() {
        Id accountId = anExistingAccount("500.00");

        asCustomer()
                .header("Idempotency-Key", "e2e-deposit-zero")
                .contentType("application/json")
                .body(Map.of("amount", 0.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/deposits", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("detail", equalTo("Amount must be positive"));

        assertEquals(new BigDecimal("500.00"), balanceOf(accountId));
    }

    @Test
    @DisplayName("moves money once when the same idempotency key is repeated")
    void movesMoneyOnceWhenTheSameIdempotencyKeyIsRepeated() {
        Id sourceId = anExistingAccount("500.00");
        Id destinationId = anExistingAccount("200.00");

        String transactionId = asCustomer()
                .header("Idempotency-Key", "e2e-transfer-1")
                .contentType("application/json")
                .body(Map.of(
                        "destinationAccountId", destinationId.getValue(),
                        "amount", 100.00,
                        "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/transfers", sourceId.getValue())
                .then()
                .statusCode(201)
                .body("sourceBalance", equalTo(400.00f))
                .body("destinationBalance", equalTo(300.00f))
                .extract().path("transactionId");

        asCustomer()
                .header("Idempotency-Key", "e2e-transfer-1")
                .contentType("application/json")
                .body(Map.of(
                        "destinationAccountId", destinationId.getValue(),
                        "amount", 100.00,
                        "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/transfers", sourceId.getValue())
                .then()
                .statusCode(201)
                .body("transactionId", equalTo(transactionId))
                .body("sourceBalance", equalTo(400.00f))
                .body("destinationBalance", equalTo(300.00f));

        assertEquals(new BigDecimal("400.00"), balanceOf(sourceId));
        assertEquals(new BigDecimal("300.00"), balanceOf(destinationId));
    }

    @Test
    @DisplayName("returns 422 when the destination is closed and does not debit the source")
    void returns422WhenTheDestinationIsClosedAndDoesNotDebitTheSource() {
        Id sourceId = anExistingAccount("500.00");
        Id destinationId = anExistingAccount("200.00");
        Account destination = accountRepository.findById(destinationId).orElseThrow();
        destination.close();
        accountRepository.save(destination);

        asCustomer()
                .header("Idempotency-Key", "e2e-transfer-closed")
                .contentType("application/json")
                .body(Map.of(
                        "destinationAccountId", destinationId.getValue(),
                        "amount", 100.00,
                        "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/transfers", sourceId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");

        assertEquals(new BigDecimal("500.00"), balanceOf(sourceId));
        assertEquals(new BigDecimal("200.00"), balanceOf(destinationId));
    }

    @Test
    @DisplayName("debits the payer and leaves the other account unchanged")
    void debitsThePayerAndLeavesTheOtherAccountUnchanged() {
        Id payerId = anExistingAccount("500.00");
        Id otherId = anExistingAccount("200.00");

        asCustomer()
                .header("Idempotency-Key", "e2e-payment-1")
                .contentType("application/json")
                .body(Map.of("recipient", "North Grid", "amount", 80.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/external-payments", payerId.getValue())
                .then()
                .statusCode(201)
                .body("newBalance", equalTo(420.00f))
                .body("recipient", equalTo("North Grid"));

        assertEquals(new BigDecimal("420.00"), balanceOf(payerId));
        assertEquals(new BigDecimal("200.00"), balanceOf(otherId));
    }

    private RequestSpecification asCustomer() {
        return given()
                .header("X-Service-Credential", "dev-service-credential-web")
                .header("Authorization", "Bearer " + tokenAdapter.issue(ownerId.getValue(), Channel.WEB, null));
    }

    private Id anExistingAccount(String balance) {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId,
                AccountNumber.create(randomAccountNumber()),
                ownerId,
                Money.create(new BigDecimal(balance), "USD")));
        return accountId;
    }

    private String randomAccountNumber() {
        long suffix = Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
        return String.valueOf(1_000_000_000L + suffix);
    }

    private BigDecimal balanceOf(Id accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance().getAmount();
    }
}
