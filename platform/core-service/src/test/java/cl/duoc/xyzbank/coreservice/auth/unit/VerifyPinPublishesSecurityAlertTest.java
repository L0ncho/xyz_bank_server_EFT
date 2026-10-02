package cl.duoc.xyzbank.coreservice.auth.unit;

import cl.duoc.xyzbank.coredomain.auth.unit.InMemoryCardRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.cards.domain.services.PinHasher;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationOutcome;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.VerifyPinUseCase;
import cl.duoc.xyzbank.coreservice.events.application.dto.CardBlocked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The VerifyPin use case security alert")
class VerifyPinPublishesSecurityAlertTest {

    /*
     * Cases:
     * 1. The failure that locks the card publishes one alert, without the PIN
     * 2. An incorrect PIN that leaves the card unlocked publishes nothing
     * 3. A card that is already locked publishes nothing
     */

    private static final String PIN = "1234";
    private static final String WRONG_PIN = "9999";

    private final cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher bcryptHasher =
            new cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher();
    private final PinHasher hasher = bcryptHasher::matches;
    private final InMemoryCardRepository cardRepository = new InMemoryCardRepository();
    private final List<CardBlocked> publishedAlerts = new ArrayList<>();
    private final VerifyPinUseCase useCase =
            new VerifyPinUseCase(cardRepository, hasher, publishedAlerts::add);

    @Test
    @DisplayName("publishes one alert without the pin when the third failure locks the card")
    void publishesOneAlertWithoutThePinWhenTheThirdFailureLocksTheCard() {
        Id cardNumber = Id.create("aa0bb1cc-22dd-4ee5-8ff6-001122334455");
        cardRepository.save(Card.create(cardNumber, Id.generate(), bcryptHasher.hash(PIN), 2, false, 0L));

        PinVerificationOutcome outcome = useCase.execute(cardNumber.getValue(), WRONG_PIN);

        assertEquals(PinVerificationOutcome.Result.LOCKED, outcome.result());
        assertNull(outcome.customerId());
        assertEquals(1, publishedAlerts.size());
        CardBlocked alert = publishedAlerts.getFirst();
        assertEquals(cardNumber.getValue(), alert.cardId());
        assertEquals(cardNumber.getValue() + ":blocked", alert.eventId());
        assertTrue(cardRepository.findByCardNumber(cardNumber).orElseThrow().isLocked());
        assertAlertOmitsThePin(alert);
    }

    @Test
    @DisplayName("publishes nothing when an incorrect pin leaves the card unlocked")
    void publishesNothingWhenAnIncorrectPinLeavesTheCardUnlocked() {
        Id cardNumber = Id.create("aa0bb1cc-22dd-4ee5-8ff6-001122334456");
        cardRepository.save(Card.create(cardNumber, Id.generate(), bcryptHasher.hash(PIN), 0, false, 0L));

        PinVerificationOutcome outcome = useCase.execute(cardNumber.getValue(), WRONG_PIN);

        assertEquals(PinVerificationOutcome.Result.INCORRECT, outcome.result());
        assertTrue(publishedAlerts.isEmpty());
    }

    @Test
    @DisplayName("publishes nothing when the card was already locked")
    void publishesNothingWhenTheCardWasAlreadyLocked() {
        Id cardNumber = Id.create("aa0bb1cc-22dd-4ee5-8ff6-001122334457");
        cardRepository.save(Card.create(cardNumber, Id.generate(), bcryptHasher.hash(PIN), 3, true, 0L));

        PinVerificationOutcome outcome = useCase.execute(cardNumber.getValue(), PIN);

        assertEquals(PinVerificationOutcome.Result.LOCKED, outcome.result());
        assertTrue(publishedAlerts.isEmpty());
    }

    private static void assertAlertOmitsThePin(CardBlocked alert) {
        String rendered = alert.eventId() + alert.cardId() + alert.occurredAt();
        assertFalse(rendered.contains(PIN));
        assertFalse(rendered.contains(WRONG_PIN));
    }
}
