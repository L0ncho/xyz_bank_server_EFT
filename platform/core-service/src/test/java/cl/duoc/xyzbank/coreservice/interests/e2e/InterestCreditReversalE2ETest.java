package cl.duoc.xyzbank.coreservice.interests.e2e;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The interest credit reversal")
class InterestCreditReversalE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Reversing an applied credit returns 200 and the balance without that credit
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
    @DisplayName("returns the balance without the applied interest")
    void returnsTheBalanceWithoutTheAppliedInterest() {
        Id accountId = anExistingAccount("1000.00");

        asInterestsService()
                .header("Idempotency-Key", "interest-reversal-e2e")
                .contentType("application/json")
                .body(Map.of(
                        "year", 2025,
                        "amount", 35.00,
                        "currency", "USD",
                        "interestRate", 0.035,
                        "openingBalance", 1000.00,
                        "closingBalance", 1035.00))
                .when()
                .post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(201);

        asInterestsService()
                .contentType("application/json")
                .body(Map.of("year", 2025))
                .when()
                .post("/internal/accounts/{accountId}/interest-credit-reversals", accountId.getValue())
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("newBalance", equalTo(1000.00f));
    }

    private io.restassured.specification.RequestSpecification asInterestsService() {
        return given()
                .header("X-Service-Credential", "dev-service-credential-interests")
                .header("Authorization", "Bearer " + tokenAdapter.issue("interests-service", Channel.INTERESTS, null));
    }

    private Id anExistingAccount(String balance) {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId,
                AccountNumber.create(String.valueOf(1000000000L + Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 1000000000L))),
                ownerId,
                Money.create(new BigDecimal(balance), "USD")));
        return accountId;
    }
}
