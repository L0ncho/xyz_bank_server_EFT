package cl.duoc.xyzbank.interestsservice.interests.infrastructure.kafka;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class InterestCreditResultListener {

    private final InterestCreditResultProcessor interestCreditResultProcessor;

    public InterestCreditResultListener(InterestCreditResultProcessor interestCreditResultProcessor) {
        this.interestCreditResultProcessor = interestCreditResultProcessor;
    }

    @KafkaListener(topics = "${interests.kafka.credit-results-topic}")
    public void onCreditResult(String payload) throws IOException {
        interestCreditResultProcessor.process(payload);
    }

    static InterestCalculationStatus statusOf(String eventType) {
        if ("InterestCreditRejected".equals(eventType)) {
            return InterestCalculationStatus.REJECTED;
        }
        if ("InterestCreditApplied".equals(eventType)) {
            return InterestCalculationStatus.APPLIED;
        }
        if ("InterestCreditReversed".equals(eventType)) {
            return InterestCalculationStatus.REVERSED;
        }
        return null;
    }
}
