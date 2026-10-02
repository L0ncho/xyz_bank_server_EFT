package cl.duoc.xyzbank.bffweb.shared.config;

import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.BearerTokenClientInterceptor;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.CorrelationIdClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

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
}
