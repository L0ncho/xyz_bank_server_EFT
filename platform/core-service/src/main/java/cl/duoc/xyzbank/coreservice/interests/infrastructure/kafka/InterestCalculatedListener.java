package cl.duoc.xyzbank.coreservice.interests.infrastructure.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class InterestCalculatedListener {

    private final InterestCalculatedProcessor interestCalculatedProcessor;

    public InterestCalculatedListener(InterestCalculatedProcessor interestCalculatedProcessor) {
        this.interestCalculatedProcessor = interestCalculatedProcessor;
    }

    @KafkaListener(topics = "${interests.kafka.calculated-topic}")
    public void onInterestCalculated(String payload) throws IOException {
        interestCalculatedProcessor.process(payload);
    }
}
