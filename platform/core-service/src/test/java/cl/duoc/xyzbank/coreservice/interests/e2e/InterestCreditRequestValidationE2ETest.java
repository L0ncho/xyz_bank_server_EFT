package cl.duoc.xyzbank.coreservice.interests.e2e;

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

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The interest credit request validation")
class InterestCreditRequestValidationE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. A body without an amount returns 400 problem+json
     */

    @LocalServerPort
    private int port;

    @Autowired
    private JwtCallerContextAdapter tokenAdapter;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("returns 400 when the interest credit body has no amount")
    void returns400WhenTheInterestCreditBodyHasNoAmount() {
        given()
                .header("X-Service-Credential", "dev-service-credential-interests")
                .header("Authorization", "Bearer " + tokenAdapter.issue("interests-service", Channel.INTERESTS, null))
                .header("Idempotency-Key", "interest-validation-missing-amount")
                .contentType("application/json")
                .body(Map.of(
                        "year", 2025,
                        "currency", "USD",
                        "interestRate", 0.035,
                        "openingBalance", 1000.00,
                        "closingBalance", 1035.00))
                .when()
                .post("/internal/accounts/{accountId}/interest-credits", "22222222-2222-2222-2222-222222222222")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("detail", equalTo("Request is invalid"));
    }
}
