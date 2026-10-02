package cl.duoc.xyzbank.coreservice.events.config;

import cl.duoc.xyzbank.coreservice.events.application.dto.CardBlocked;
import cl.duoc.xyzbank.coreservice.events.application.dto.TransactionConfirmed;
import cl.duoc.xyzbank.coreservice.events.application.ports.SecurityAlertPublisher;
import cl.duoc.xyzbank.coreservice.events.application.ports.TransactionConfirmedPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventsConfig {

    @Bean
    @ConditionalOnProperty(
            name = "app.events.transaction-confirmed.enabled",
            havingValue = "false",
            matchIfMissing = true)
    public TransactionConfirmedPublisher noOpTransactionConfirmedPublisher() {
        return new TransactionConfirmedPublisher() {
            @Override
            public void publish(TransactionConfirmed event) {
            }
        };
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.events.security-alerts.enabled",
            havingValue = "false",
            matchIfMissing = true)
    public SecurityAlertPublisher noOpSecurityAlertPublisher() {
        return new SecurityAlertPublisher() {
            @Override
            public void publish(CardBlocked alert) {
            }
        };
    }
}
