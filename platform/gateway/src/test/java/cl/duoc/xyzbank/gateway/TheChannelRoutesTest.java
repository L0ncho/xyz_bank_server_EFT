package cl.duoc.xyzbank.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("The channel routes")
class TheChannelRoutesTest {

    private static final char[] devKeystorePassword = "xyzbank-dev".toCharArray();

    private static HttpsServer channelBff;

    @Autowired
    private WebTestClient gateway;

    @BeforeAll
    static void startChannelBffWithDevelopmentCertificate() throws Exception {
        channelBff = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        channelBff.setHttpsConfigurator(new HttpsConfigurator(developmentCertificateContext()));
        channelBff.createContext("/", TheChannelRoutesTest::answerWithReceivedPath);
        channelBff.start();
    }

    @AfterAll
    static void stopChannelBff() {
        channelBff.stop(0);
    }

    @DynamicPropertySource
    static void routeChannelsToTheBff(DynamicPropertyRegistry registry) {
        String channelBffUrl = "https://localhost:" + channelBff.getAddress().getPort();
        registry.add("BFF_WEB_BASE_URL", () -> channelBffUrl);
        registry.add("BFF_MOBILE_BASE_URL", () -> channelBffUrl);
    }

    @Test
    void forwardsWebChannelRequestsToItsBffOverTlsWithoutTheChannelPrefix() {
        gateway.get().uri("/web/customers/11111111-1111-1111-1111-111111111111/dashboard")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("/customers/11111111-1111-1111-1111-111111111111/dashboard");
    }

    @Test
    void forwardsMobileChannelRequestsToItsBffOverTlsWithoutTheChannelPrefix() {
        gateway.get().uri("/mobile/accounts/22222222-2222-2222-2222-222222222222/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("/accounts/22222222-2222-2222-2222-222222222222/summary");
    }

    private static SSLContext developmentCertificateContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream keystore = TheChannelRoutesTest.class.getResourceAsStream("/tls/keystore.p12")) {
            keyStore.load(keystore, devKeystorePassword);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, devKeystorePassword);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }

    private static void answerWithReceivedPath(HttpExchange exchange) throws IOException {
        byte[] receivedPath = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, receivedPath.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(receivedPath);
        }
    }
}
