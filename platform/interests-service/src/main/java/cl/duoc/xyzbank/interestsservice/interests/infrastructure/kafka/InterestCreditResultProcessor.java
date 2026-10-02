package cl.duoc.xyzbank.interestsservice.interests.infrastructure.kafka;

import cl.duoc.xyzbank.interestsservice.interests.application.dto.InterestCreditResult;
import cl.duoc.xyzbank.interestsservice.interests.application.usecases.RecordInterestCreditResultUseCase;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class InterestCreditResultProcessor {

    private final RecordInterestCreditResultUseCase recordInterestCreditResultUseCase;
    private final ObjectMapper objectMapper;

    public InterestCreditResultProcessor(
            RecordInterestCreditResultUseCase recordInterestCreditResultUseCase,
            ObjectMapper objectMapper) {
        this.recordInterestCreditResultUseCase = recordInterestCreditResultUseCase;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "interestEventProcessing", fallbackMethod = "onFailure")
    public void process(String payload) throws IOException {
        JsonNode result = objectMapper.readTree(payload);
        String eventType = result.get("eventType").asText();
        InterestCalculationStatus status = InterestCreditResultListener.statusOf(eventType);
        if (status == null) {
            return;
        }
        String reason = result.hasNonNull("reason") ? result.get("reason").asText() : null;
        recordInterestCreditResultUseCase.execute(new InterestCreditResult(
                result.get("eventId").asText(), status, reason));
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
}
