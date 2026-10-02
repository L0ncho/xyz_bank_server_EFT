package cl.duoc.xyzbank.coreservice.accounts.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The account lifecycle controller")
class OpenAccountControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Opens an account
     * 2. Returns 409 when the account number already exists and leaves the seed account unchanged
     */

    private static final String seedAccountId = "22222222-2222-2222-2222-222222222222";
    private static final String seedCustomerId = "11111111-1111-1111-1111-111111111111";
    private static final String seedAccountNumber = "1000000001";

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JwtCallerContextAdapter tokenAdapter;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("opens an account")
    void opensAnAccount() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(
                customerId, "Jane Doe", "jane.doe+" + customerId.getValue() + "@xyzbank.cl"));
        String accountNumber = "2000000001";

        asCustomer(customerId)
                .contentType("application/json")
                .body("""
                        {
                          "accountNumber": "%s",
                          "customerId": "%s",
                          "balance": 0.00,
                          "currency": "USD"
                        }
                        """.formatted(accountNumber, customerId.getValue()))
                .when().post("/internal/accounts")
                .then()
                .statusCode(201)
                .body("accountNumber", equalTo(accountNumber))
                .body("customerId", equalTo(customerId.getValue()))
                .body("balance", equalTo(0.0f))
                .body("currency", equalTo("USD"))
                .body("status", equalTo("OPEN"));
    }

    @Test
    @DisplayName("returns 409 when the account number already exists and leaves the seed account unchanged")
    void returnsConflictWhenTheAccountNumberAlreadyExistsAndLeavesTheSeedAccountUnchanged() {
        asCustomer(Id.create(seedCustomerId))
                .contentType("application/json")
                .body("""
                        {
                          "accountNumber": "%s",
                          "customerId": "%s",
                          "balance": 1.00,
                          "currency": "USD"
                        }
                        """.formatted(seedAccountNumber, seedCustomerId))
                .when().post("/internal/accounts")
                .then()
                .statusCode(409)
                .contentType("application/problem+json");

        var seed = accountRepository.findById(Id.create(seedAccountId)).orElseThrow();
        assertEquals(seedAccountNumber, seed.getAccountNumber().getValue());
        assertEquals(new BigDecimal("1500.00"), seed.getBalance().getAmount());
        assertEquals(AccountNumber.create(seedAccountNumber), seed.getAccountNumber());
    }

    private RequestSpecification asCustomer(Id customerId) {
        return given()
                .header("X-Service-Credential", "dev-service-credential-web")
                .header("Authorization", "Bearer " + tokenAdapter.issue(customerId.getValue(), Channel.WEB, null));
    }
}
