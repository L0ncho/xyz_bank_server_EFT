package cl.duoc.xyzbank.bffatm.shared.config;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.BearerTokenClientInterceptor;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.CorrelationIdClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;

@Configuration
public class CoreServiceClientConfig {

    @Bean
    @LoadBalanced
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "true")
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "false", matchIfMissing = true)
    public RestClient.Builder plainRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Primary
    public RestClient coreServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${core-service.base-url}") String baseUrl,
            @Value("${core-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${core-service.read-timeout-ms}") int readTimeoutMs,
            @Value("${core-service.service-credential}") String serviceCredential,
            CorrelationIdClientInterceptor correlationIdClientInterceptor,
            BearerTokenClientInterceptor bearerTokenClientInterceptor) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Service-Credential", serviceCredential)
                .requestInterceptor(correlationIdClientInterceptor)
                .requestInterceptor(bearerTokenClientInterceptor)
                .build();
    }

    /**
     * Used only for {@code POST /internal/auth/atm/pin-verifications}, core-service's
     * TLS-only PIN-verification connector (design.md Decision 8). Trusts the same shared
     * dev CA {@code bff-atm} already trusts for its own mTLS connector, since core-service's
     * PIN-verification certificate is signed by that same CA.
     */
    @Bean
    public RestClient corePinVerificationClient(
            @Value("${core-service.pin-verification-base-url}") String baseUrl,
            @Value("${core-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${core-service.read-timeout-ms}") int readTimeoutMs,
            @Value("${core-service.service-credential}") String serviceCredential,
            @Value("${server.ssl.trust-store}") String trustStorePath,
            @Value("${server.ssl.trust-store-password}") String trustStorePassword,
            ResourceLoader resourceLoader,
            CorrelationIdClientInterceptor correlationIdClientInterceptor)
            throws GeneralSecurityException, IOException {
        SSLContext sslContext = trustingSslContext(resourceLoader, trustStorePath, trustStorePassword);
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Service-Credential", serviceCredential)
                .requestInterceptor(correlationIdClientInterceptor)
                .build();
    }

    private static SSLContext trustingSslContext(ResourceLoader resourceLoader, String trustStorePath, String trustStorePassword)
            throws GeneralSecurityException, IOException {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = resourceLoader.getResource(trustStorePath).getInputStream()) {
            trustStore.load(in, trustStorePassword.toCharArray());
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
}
