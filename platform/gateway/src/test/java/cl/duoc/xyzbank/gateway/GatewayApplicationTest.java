package cl.duoc.xyzbank.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@DisplayName("Gateway")
class GatewayApplicationTest {

    @Test
    @DisplayName("starts with the channel routes on port 8090")
    void startsWithTheChannelRoutesOnPort8090() throws Exception {
        try (InputStream input = GatewayApplicationTest.class.getClassLoader().getResourceAsStream("application.yml")) {
            assertNotNull(input);
            Map<String, Object> document = new Yaml().load(input);
            assertEquals(8090, child(document, "server").get("port"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }
}
