package cl.duoc.xyzbank.coreservice.interests.unit;

import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditRejected;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditReversed;
import cl.duoc.xyzbank.coreservice.interests.application.ports.InterestCreditResultPublisher;

import java.util.ArrayList;
import java.util.List;

public class InMemoryInterestCreditResultPublisher implements InterestCreditResultPublisher {

    private final List<InterestCreditRejected> rejections = new ArrayList<>();
    private final List<InterestCreditReversed> reversals = new ArrayList<>();

    @Override
    public void reject(InterestCreditRejected rejection) {
        rejections.add(rejection);
    }

    @Override
    public void reverse(InterestCreditReversed reversal) {
        reversals.add(reversal);
    }

    public List<InterestCreditRejected> rejections() {
        return List.copyOf(rejections);
    }

    public List<InterestCreditReversed> reversals() {
        return List.copyOf(reversals);
    }
}
