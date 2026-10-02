package cl.duoc.xyzbank.coreservice.events.application.ports;

import cl.duoc.xyzbank.coreservice.events.application.dto.CardBlocked;

@FunctionalInterface
public interface SecurityAlertPublisher {

    void publish(CardBlocked alert);
}
