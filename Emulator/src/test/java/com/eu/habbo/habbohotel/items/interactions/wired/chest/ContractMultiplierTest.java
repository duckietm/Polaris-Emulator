package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.OfferedItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingSession.Confirmation;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Initiate Transaction's multiplier modes on the negotiation: the fixed multiplier asks for the price that
 * many times over, the auto multiplier settles as many times as the offer covers, up to its limit.
 */
class ContractMultiplierTest {
    private static final int CHAIR = 1389;
    private static final int TROPHY = 900;

    private static final class Vault implements WiredTradingSession.ItemVault {
        private final Map<Integer, OfferedItem> inventory = new LinkedHashMap<>();

        Vault chairs(int count) {
            for (int i = 0; i < count; i++) this.inventory.put(100 + i, new OfferedItem(100 + i, false, CHAIR));
            return this;
        }

        @Override
        public boolean take(int itemId) {
            return this.inventory.containsKey(itemId);
        }

        @Override
        public void giveBack(int itemId) {}

        @Override
        public OfferedItem describe(int itemId) {
            return this.inventory.get(itemId);
        }
    }

    /** Two chairs for one trophy. */
    private static ContractRules twoChairsForATrophy() {
        return ContractRules.of(
                List.of(List.of(Term.furni(InteractionWiredContract.DIR_PAY, false, CHAIR, "", 2))),
                List.of(Term.furni(InteractionWiredContract.DIR_RECEIVE, false, TROPHY, "", 1)));
    }

    private static WiredTradingSession session(ContractMultiplier multiplier, int chairs) {
        Vault vault = new Vault().chairs(chairs);
        WiredTradingSession session =
                new WiredTradingSession(twoChairsForATrophy(), multiplier, vault, type -> 0, 60, 0L);
        session.offerItems(List.copyOf(vault.inventory.keySet()));
        return session;
    }

    private static Confirmation confirm(WiredTradingSession session) {
        assertTrue(session.accept(0L), "the offer can be accepted");
        return session.confirm(WiredTradingSession.CONFIRM_DELAY_MILLIS);
    }

    @Test
    void theMultiplierIsHeldToTheOfficialRange() {
        assertEquals(1, new ContractMultiplier(ContractMultiplier.MODE_FIXED, 0).value());
        assertEquals(500, new ContractMultiplier(ContractMultiplier.MODE_AUTO, 9_999).value());
        assertEquals(1, new ContractMultiplier(ContractMultiplier.MODE_NORMAL, 40).value(), "normal runs once");
        assertEquals(ContractMultiplier.MODE_NORMAL, new ContractMultiplier(7, 3).mode());
    }

    @Test
    void timesScalesBothSidesAndKeepsTheRuleOrder() {
        ContractRules thrice = twoChairsForATrophy().times(3);

        assertEquals(6, thrice.giveRules().get(0).get(0).amount);
        assertEquals(3, thrice.getRule().get(0).amount);
        assertEquals(TROPHY, thrice.getRule().get(0).baseItemId);
        assertEquals(2, twoChairsForATrophy().giveRules().get(0).get(0).amount, "the contract itself is untouched");
    }

    @Test
    void aFixedMultiplierAsksForThePriceThatManyTimes() {
        ContractMultiplier three = new ContractMultiplier(ContractMultiplier.MODE_FIXED, 3);

        WiredTradingSession short1 = session(three, 5);
        assertFalse(short1.canAccept(), "five chairs do not pay three times two");
        assertEquals(2, short1.timesMet(), "the window's met count");

        WiredTradingSession enough = session(three, 6);
        assertEquals(3, enough.timesMet());
        Confirmation confirmation = confirm(enough);
        assertTrue(confirmation.settled());
        assertEquals(3, enough.settledTimes());
        assertEquals(6, confirmation.match().consumedItemIds().size());
    }

    @Test
    void theAutoMultiplierSettlesWhatTheOfferCoversUpToItsLimit() {
        ContractMultiplier upToFour = new ContractMultiplier(ContractMultiplier.MODE_AUTO, 4);

        assertFalse(session(upToFour, 1).canAccept(), "it must pay at least once");

        WiredTradingSession six = session(upToFour, 6);
        assertEquals(3, six.timesToSettle(), "six chairs pay two chairs three times");
        assertEquals(3, six.effectiveRules().getRule().get(0).amount);
        assertTrue(confirm(six).settled());
        assertEquals(3, six.settledTimes());

        // Seven chairs lie between three and four runs: AIR's "met" needs a whole number of runs.
        WiredTradingSession seven = session(upToFour, 7);
        assertEquals(0, seven.timesMet());
        assertFalse(seven.canAccept());

        // The table takes no more than the limit asks: eight of twenty chairs, four runs.
        WiredTradingSession plenty = session(upToFour, 20);
        assertEquals(8, plenty.getOfferedItems().size(), "never more than the limit");
        assertEquals(4, plenty.timesToSettle());
    }

    @Test
    void aNegotiationWithoutTimeoutNeverLapses() {
        WiredTradingSession session =
                new WiredTradingSession(twoChairsForATrophy(), ContractMultiplier.NONE, new Vault(), type -> 0, 0, 0L);

        assertFalse(session.hasTimeout());
        assertFalse(session.hasExpired(Long.MAX_VALUE / 2));

        WiredTradingSession timed = new WiredTradingSession(twoChairsForATrophy(), new Vault(), type -> 0, 0, 0L);
        assertTrue(timed.hasTimeout(), "the old constructor keeps its one second floor");
        assertTrue(timed.hasExpired(1_000L));
    }
}
