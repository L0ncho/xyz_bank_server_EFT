package cl.duoc.xyzbank.coreservice.shared.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("core-service runtime configuration")
class CoreServiceRuntimeConfigTest {

    @Test
    @DisplayName("starts without Config Server because the import is optional")
    void startsWithoutConfigServerBecauseTheImportIsOptional() throws IOException {
        Path[] candidates = {
                Path.of("src/main/resources/application.yml"),
                Path.of("platform/core-service/src/main/resources/application.yml")
        };
        Path yamlPath = null;
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                yamlPath = candidate;
                break;
            }
        }
        if (yamlPath == null) {
            throw new IllegalStateException("Could not locate core-service application.yml");
        }
        String yaml = Files.readString(yamlPath);

        assertTrue(yaml.contains("optional:configserver:"), yaml);
    }
}
