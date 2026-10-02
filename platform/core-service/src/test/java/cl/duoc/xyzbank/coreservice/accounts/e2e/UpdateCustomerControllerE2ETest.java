package cl.duoc.xyzbank.coreservice.accounts.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The customer profile update controller")
class UpdateCustomerControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Updates the name and email, and the existing profile GET returns them
     * 2. Returns 404 for an unknown customer
     * 3. Returns 422 for an invalid email and leaves the stored profile unchanged
     */

    @LocalServerPort
    private int port;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JwtCallerContextAdapter tokenAdapter;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("updates the name and email, and the existing profile GET returns them")
    void updatesTheNameAndEmailAndTheExistingProfileGetReturnsThem() {
        Id id = Id.generate();
        customerRepository.save(Customer.create(id, "Jane Doe", "jane.doe@xyzbank.cl"));

        asOwner(id)
                .contentType("application/json")
                .body("""
                        {
                          "fullName": "Ana Soto",
                          "email": "ana.soto@xyzbank.cl"
                        }
                        """)
                .when().put("/internal/customers/{customerId}", id.getValue())
                .then()
                .statusCode(200)
                .body("id", equalTo(id.getValue()))
                .body("fullName", equalTo("Ana Soto"))
                .body("email", equalTo("ana.soto@xyzbank.cl"));

        asOwner(id)
                .when().get("/internal/customers/{customerId}", id.getValue())
                .then()
                .statusCode(200)
                .body("id", equalTo(id.getValue()))
                .body("fullName", equalTo("Ana Soto"))
                .body("email", equalTo("ana.soto@xyzbank.cl"));
    }

    @Test
    @DisplayName("returns 404 for an unknown customer")
    void returnsNotFoundForAnUnknownCustomer() {
        Id id = Id.generate();

        asOwner(id)
                .contentType("application/json")
                .body("""
                        {
                          "fullName": "Ana Soto",
                          "email": "ana.soto@xyzbank.cl"
                        }
                        """)
                .when().put("/internal/customers/{customerId}", id.getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for an invalid email and leaves the stored profile unchanged")
    void returnsUnprocessableEntityForAnInvalidEmailAndLeavesTheStoredProfileUnchanged() {
        Id id = Id.generate();
        customerRepository.save(Customer.create(id, "Jane Doe", "jane.doe@xyzbank.cl"));

        asOwner(id)
                .contentType("application/json")
                .body("""
                        {
                          "fullName": "Ana Soto",
                          "email": "not-an-email"
                        }
                        """)
                .when().put("/internal/customers/{customerId}", id.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");

        asOwner(id)
                .when().get("/internal/customers/{customerId}", id.getValue())
                .then()
                .statusCode(200)
                .body("fullName", equalTo("Jane Doe"))
                .body("email", equalTo("jane.doe@xyzbank.cl"));
    }

    private RequestSpecification asOwner(Id customerId) {
        return given()
                .header("X-Service-Credential", "dev-service-credential-web")
                .header("Authorization", "Bearer " + tokenAdapter.issue(customerId.getValue(), Channel.WEB, null));
    }
}
