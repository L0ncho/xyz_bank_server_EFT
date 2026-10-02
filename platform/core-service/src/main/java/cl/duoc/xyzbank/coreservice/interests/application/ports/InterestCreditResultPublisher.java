package cl.duoc.xyzbank.coreservice.interests.application.ports;

import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditRejected;
import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditReversed;

public interface InterestCreditResultPublisher {

    void reject(InterestCreditRejected rejection);

    void reverse(InterestCreditReversed reversal);
}
