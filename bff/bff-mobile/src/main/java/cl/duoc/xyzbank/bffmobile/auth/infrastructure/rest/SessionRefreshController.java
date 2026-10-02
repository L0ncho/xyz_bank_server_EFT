package cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest;

import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.dto.MobileRefreshRequest;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.dto.MobileSessionResponse;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.dto.RefreshTokenRequest;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.dto.RefreshTokenResponse;
import cl.duoc.xyzbank.bffmobile.shared.infrastructure.adapters.CoreServiceCalls;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import org.springframework.web.client.RestClient;

/**
 * Rotates the mobile client's refresh token through core-service and mints a fresh
 * device-bound session JWT, handed back directly in the response body -- no cookie exists to
 * update, unlike bff-web's refresh endpoint (design.md Decision 4).
 */
@RestController
public class SessionRefreshController {

    private final JwtCallerContextAdapter tokenAdapter;
    private final RestClient coreServiceClient;

    public SessionRefreshController(JwtCallerContextAdapter tokenAdapter, RestClient coreServiceClient) {
        this.tokenAdapter = tokenAdapter;
        this.coreServiceClient = coreServiceClient;
    }

    @PostMapping("/session/refresh")
    public MobileSessionResponse refresh(@Valid @RequestBody MobileRefreshRequest request) {
        RefreshTokenResponse refreshTokenResponse = CoreServiceCalls.fetch(() -> coreServiceClient
                .post()
                .uri("/internal/auth/mobile/devices/{deviceId}/refresh-tokens", request.deviceId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshTokenRequest(null, request.refreshToken()))
                .retrieve()
                .body(RefreshTokenResponse.class));

        String sessionJwt = tokenAdapter.issue(refreshTokenResponse.customerId(), Channel.MOBILE, request.deviceId());
        return new MobileSessionResponse(sessionJwt, refreshTokenResponse.refreshToken(), refreshTokenResponse.expiry());
    }
}
