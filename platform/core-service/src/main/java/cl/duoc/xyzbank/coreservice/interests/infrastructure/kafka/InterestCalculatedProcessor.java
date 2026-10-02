package cl.duoc.xyzbank.coreservice.interests.infrastructure.kafka;

import cl.duoc.xyzbank.coreservice.interests.application.dto.CreditInterestRequest;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.CreditInterestUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class InterestCalculatedProcessor {

    private final CreditInterestUseCase creditInterestUseCase;
    private final ObjectMapper objectMapper;

    public InterestCalculatedProcessor(CreditInterestUseCase creditInterestUseCase, ObjectMapper objectMapper) {
        this.creditInterestUseCase = creditInterestUseCase;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "interestEventProcessing", fallbackMethod = "onFailure")
    public void process(String payload) throws IOException {
        InterestCalculated event = objectMapper.readValue(payload, InterestCalculated.class);
        creditInterestUseCase.executeFromEvent(toRequest(event), event.eventId());
    }

    @SuppressWarnings("unused")
    private void onFailure(String payload, Throwable throwable) throws IOException {
        if (throwable instanceof IOException ioException) {
            throw ioException;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IOException(throwable);
    }

    private CreditInterestRequest toRequest(InterestCalculated event) {
        return new CreditInterestRequest(
                event.accountId(),
                event.period(),
                event.amount(),
                event.currency(),
                event.interestRate(),
                event.openingBalance(),
                event.closingBalance(),
                event.eventId());
    }
}
