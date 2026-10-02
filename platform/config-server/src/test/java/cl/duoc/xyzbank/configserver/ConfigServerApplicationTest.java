package cl.duoc.xyzbank.configserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Config Server Application")
class ConfigServerApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void nativeConfigRepo(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.config.server.native.search-locations",
                () -> "file:" + locateConfigRepo());
    }

    private static Path locateConfigRepo() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            Path candidate = cursor.resolve("config-repo");
            if (Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("interests-service.yml"))) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Could not locate config-repo/interests-service.yml from " + Path.of("").toAbsolutePath());
    }

    @Test
    @DisplayName("loads the Spring context successfully")
    void contextLoads() {
        // Smoke test: verifies the application starts without errors
    }

    @Test
    @DisplayName("serves interests-service default configuration from the native repository")
    void servesInterestsServiceDefaultConfiguration() {
        ResponseEntity<String> response = restTemplate.getForEntity("/interests-service/default", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("core-service"), response.getBody());
        assertTrue(response.getBody().contains("coreServiceInterests")
                || response.getBody().contains("resilience4j"), response.getBody());
    }

    @Test
    @DisplayName("does not invent interests-service property values for an unknown application")
    void doesNotInventInterestsServicePropertiesForUnknownApplication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/unknown-semana6-app/default", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(
                response.getBody().contains("coreServiceInterests"),
                "unknown apps must not receive interests-service resilience settings: " + response.getBody());
    }

    @Test
    @DisplayName("serves core-service topics and does not give them to an unknown application")
    void servesCoreServiceTopicsAndDoesNotGiveThemToAnUnknownApplication() {
        ResponseEntity<String> core = restTemplate.getForEntity("/core-service/default", String.class);
        ResponseEntity<String> unknown = restTemplate.getForEntity("/unknown-semana6-app/default", String.class);

        assertEquals(HttpStatus.OK, core.getStatusCode());
        assertNotNull(core.getBody());
        assertTrue(core.getBody().contains("interests.calculated"), core.getBody());
        assertTrue(core.getBody().contains("interestEventProcessing"), core.getBody());
        assertNotNull(unknown.getBody());
        assertFalse(unknown.getBody().contains("interestEventProcessing"), unknown.getBody());
    }
}
