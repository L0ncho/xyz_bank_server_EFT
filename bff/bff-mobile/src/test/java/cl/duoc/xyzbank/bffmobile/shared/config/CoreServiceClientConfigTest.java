package cl.duoc.xyzbank.bffmobile.shared.config;

import cl.duoc.xyzbank.bffmobile.shared.infrastructure.rest.BearerTokenClientInterceptor;
import cl.duoc.xyzbank.bffmobile.shared.infrastructure.rest.CorrelationIdClientInterceptor;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CoreServiceClientConfig")
class CoreServiceClientConfigTest {

    @Test
    @DisplayName("without Eureka the fixed test url is still used")
    void withoutEurekaTheFixedTestUrlIsStillUsed() throws Exception {
        Map<String, Object> document = loadApplicationYaml();
        Map<String, Object> eurekaClient = child(child(document, "eureka"), "client");
        Map<String, Object> coreService = child(document, "core-service");

        assertEquals("${EUREKA_CLIENT_ENABLED:false}", eurekaClient.get("enabled"));
        assertEquals(false, eurekaClient.get("register-with-eureka"));
        assertEquals("${CORE_SERVICE_BASE_URL:http://localhost:8080}", coreService.get("base-url"));
        assertEquals(8082, child(document, "server").get("port"));

        Method plain = CoreServiceClientConfig.class.getDeclaredMethod("plainRestClientBuilder");
        ConditionalOnProperty plainCondition = plain.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(plainCondition);
        assertEquals("eureka.client.enabled", plainCondition.name()[0]);
        assertEquals("false", plainCondition.havingValue());
        assertTrue(plainCondition.matchIfMissing());
        assertNull(plain.getAnnotation(LoadBalanced.class));

        WireMockServer coreServiceDouble = new WireMockServer(wireMockConfig().dynamicPort());
        coreServiceDouble.start();
        try {
            coreServiceDouble.stubFor(get(urlEqualTo("/internal/accounts/probe"))
                    .willReturn(aResponse().withStatus(204)));
            String testUrl = coreServiceDouble.baseUrl();
            RestClient client = new CoreServiceClientConfig().coreServiceClient(
                    new CoreServiceClientConfig().plainRestClientBuilder(),
                    testUrl,
                    1000,
                    1000,
                    "dev-service-credential-mobile",
                    new CorrelationIdClientInterceptor(),
                    new BearerTokenClientInterceptor());

            client.get().uri("/internal/accounts/probe").retrieve().toBodilessEntity();

            coreServiceDouble.verify(getRequestedFor(urlEqualTo("/internal/accounts/probe")));
            assertTrue(coreServiceDouble.getAllServeEvents().get(0).getRequest().getAbsoluteUrl().startsWith(testUrl));
        } finally {
            coreServiceDouble.stop();
        }
    }

    @Test
    @DisplayName("discovery-enabled RestClient builder is LoadBalanced only when Eureka is enabled")
    void discoveryEnabledBuilderIsLoadBalancedOnlyWhenEurekaIsEnabled() throws Exception {
        Method method = CoreServiceClientConfig.class.getDeclaredMethod("loadBalancedRestClientBuilder");
        LoadBalanced loadBalanced = method.getAnnotation(LoadBalanced.class);
        ConditionalOnProperty condition = method.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(loadBalanced);
        assertNotNull(condition);
        assertEquals("eureka.client.enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadApplicationYaml() throws Exception {
        try (InputStream input = CoreServiceClientConfigTest.class.getClassLoader().getResourceAsStream("application.yml")) {
            assertNotNull(input);
            return new Yaml().load(input);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }
}
