package cl.duoc.xyzbank.coreservice.auth.config;

import cl.duoc.xyzbank.coredomain.auth.domain.repositories.DeviceRegistrationRepository;
import cl.duoc.xyzbank.coredomain.auth.domain.repositories.RefreshTokenRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.services.PinHasher;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.RevokeDeviceUseCase;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.RotateMobileRefreshTokenUseCase;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.RotateWebRefreshTokenUseCase;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.VerifyPinUseCase;
import cl.duoc.xyzbank.coreservice.events.application.ports.SecurityAlertPublisher;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import cl.duoc.xyzbank.sharedsecurity.callercontext.OpaqueTokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    // Adapter: the concrete BCrypt-backed hasher (shared-security) is an infrastructure
    // detail. Only its `matches` behavior is exposed to the domain, through the PinHasher port.
    @Bean
    public PinHasher pinHasher() {
        cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher bcryptHasher =
                new cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher();
        return bcryptHasher::matches;
    }

    @Bean
    public JwtCallerContextAdapter jwtCallerContextAdapter(
            @Value("${channel-auth.jwt.secret}") String jwtSecret) {
        return new JwtCallerContextAdapter(jwtSecret);
    }

    @Bean
    public OpaqueTokenGenerator opaqueTokenGenerator() {
        return new OpaqueTokenGenerator();
    }

    @Bean
    public VerifyPinUseCase verifyPinUseCase(
            CardRepository cardRepository, PinHasher pinHasher, SecurityAlertPublisher securityAlertPublisher) {
        return new VerifyPinUseCase(cardRepository, pinHasher, securityAlertPublisher);
    }

    @Bean
    public RotateWebRefreshTokenUseCase rotateWebRefreshTokenUseCase(
            RefreshTokenRepository refreshTokenRepository, OpaqueTokenGenerator tokenGenerator) {
        return new RotateWebRefreshTokenUseCase(refreshTokenRepository, tokenGenerator);
    }

    @Bean
    public RotateMobileRefreshTokenUseCase rotateMobileRefreshTokenUseCase(
            RefreshTokenRepository refreshTokenRepository,
            DeviceRegistrationRepository deviceRegistrationRepository,
            OpaqueTokenGenerator tokenGenerator) {
        return new RotateMobileRefreshTokenUseCase(refreshTokenRepository, deviceRegistrationRepository,
                tokenGenerator);
    }

    @Bean
    public RevokeDeviceUseCase revokeDeviceUseCase(DeviceRegistrationRepository deviceRegistrationRepository) {
        return new RevokeDeviceUseCase(deviceRegistrationRepository);
    }
}
