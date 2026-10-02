package cl.duoc.xyzbank.bffatm.auth.infrastructure.rest;

import cl.duoc.xyzbank.bffatm.auth.infrastructure.rest.dto.AtmSessionResponse;
import cl.duoc.xyzbank.bffatm.auth.infrastructure.rest.dto.PinVerificationRequest;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCalls;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.TerminalIdentity;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import org.springframework.web.client.RestClient;

/**
 * Verifies a card PIN against core-service's TLS-only PIN-verification endpoint and, on
 * success, issues a 120-second ATM session bound to the terminal's mTLS certificate (design.md
 * Decision 8). core-service answers 401 and 423 with the same generic problem detail, so the
 * response does not reveal whether the card exists. This controller never inspects or logs
 * the submitted PIN.
 */
@RestController
public class PinVerificationController {

    private final RestClient corePinVerificationClient;
    private final JwtCallerContextAdapter tokenAdapter;

    public PinVerificationController(
            @Qualifier("corePinVerificationClient") RestClient corePinVerificationClient,
            JwtCallerContextAdapter tokenAdapter) {
        this.corePinVerificationClient = corePinVerificationClient;
        this.tokenAdapter = tokenAdapter;
    }

    @PostMapping("/pin-verifications")
    public AtmSessionResponse verify(@Valid @RequestBody PinVerificationRequest request, @TerminalIdentity String terminalId) {
        CorePinVerificationResponse response = CoreServiceCalls.fetch(() -> corePinVerificationClient
                .post()
                .uri("/internal/auth/atm/pin-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CorePinVerificationRequest(request.cardNumber(), request.pin()))
                .retrieve()
                .body(CorePinVerificationResponse.class));

        String sessionToken = tokenAdapter.issue(response.customerId(), Channel.ATM, terminalId);
        return new AtmSessionResponse(sessionToken);
    }

    private record CorePinVerificationRequest(String cardNumber, String pin) {
    }

    private record CorePinVerificationResponse(String customerId) {
    }
}
