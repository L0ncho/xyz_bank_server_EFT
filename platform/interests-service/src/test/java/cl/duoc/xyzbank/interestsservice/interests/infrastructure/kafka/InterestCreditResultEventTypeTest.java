package cl.duoc.xyzbank.interestsservice.interests.infrastructure.kafka;

import cl.duoc.xyzbank.interestsservice.interests.application.dto.InterestCreditResult;
import cl.duoc.xyzbank.interestsservice.interests.application.usecases.RecordInterestCreditResultUseCase;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InMemoryInterestCalculationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("The interest credit result event")
class InterestCreditResultEventTypeTest {

    /*
     * Cases:
     * 1. InterestCreditReversed closes an applied calculation as reversed
     * 2. An unknown event type is not treated as applied
     */

    @Test
    @DisplayName("closes an applied calculation as reversed")
    void closesAnAppliedCalculationAsReversed() {
        InMemoryInterestCalculationRepository calculations = new InMemoryInterestCalculationRepository();
        calculations.save(InterestCalculation.pending(
                "interest:account-1:2025", "account-1", 2025, new BigDecimal("35.00"), "USD"));
        RecordInterestCreditResultUseCase useCase = new RecordInterestCreditResultUseCase(calculations);
        useCase.execute(new InterestCreditResult(
                "interest:account-1:2025", InterestCalculationStatus.APPLIED, null));

        useCase.execute(new InterestCreditResult(
                "interest:account-1:2025", InterestCalculationStatus.REVERSED, null));

        assertEquals(
                InterestCalculationStatus.REVERSED,
                calculations.findByEventId("interest:account-1:2025").orElseThrow().status());
    }

    @Test
    @DisplayName("does not treat an unknown event type as applied")
    void doesNotTreatAnUnknownEventTypeAsApplied() {
        assertNull(InterestCreditResultListener.statusOf("InterestSomethingElse"));
        assertEquals(InterestCalculationStatus.APPLIED, InterestCreditResultListener.statusOf("InterestCreditApplied"));
        assertEquals(InterestCalculationStatus.REJECTED, InterestCreditResultListener.statusOf("InterestCreditRejected"));
        assertEquals(InterestCalculationStatus.REVERSED, InterestCreditResultListener.statusOf("InterestCreditReversed"));
    }
}
