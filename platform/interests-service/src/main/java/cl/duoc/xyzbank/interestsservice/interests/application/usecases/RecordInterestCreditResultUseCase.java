package cl.duoc.xyzbank.interestsservice.interests.application.usecases;

import cl.duoc.xyzbank.interestsservice.interests.application.dto.InterestCreditResult;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;

public class RecordInterestCreditResultUseCase {

    private final InterestCalculationRepository calculations;

    public RecordInterestCreditResultUseCase(InterestCalculationRepository calculations) {
        this.calculations = calculations;
    }

    public void execute(InterestCreditResult result) {
        InterestCalculation calculation = calculations.findByEventId(result.eventId()).orElse(null);
        if (calculation == null) {
            return;
        }
        if (result.status() == InterestCalculationStatus.REVERSED) {
            calculation.reverse();
            calculations.save(calculation);
            return;
        }
        if (calculation.isClosed()) {
            return;
        }
        if (result.status() == InterestCalculationStatus.APPLIED) {
            calculation.apply();
        } else {
            calculation.reject(result.reason());
        }
        calculations.save(calculation);
    }
}
