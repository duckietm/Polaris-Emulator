package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.OfferedItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingSession.Confirmation;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The negotiation is where a player's furniture is at risk, so these pin the moments money and items
 * can go wrong: an offer changing under an acceptance, a confirmation arriving twice, a session
 * closing while it still holds someone's chair.
 */
class WiredTradingSessionTest {

    private static final int CREDITS = -1;
    private static final int TIMEOUT = 60;

    /** A fake inventory that records what it holds, so an item can be proven returned. */
    private static final class FakeVault implements WiredTradingSession.ItemVault {
        private final Map<Integer, OfferedItem> inventory = new LinkedHashMap<>();
        private final List<Integer> held = new ArrayList<>();

        FakeVault add(int itemId, int baseItemId) {
            this.inventory.put(itemId, new OfferedItem(itemId, false, baseItemId));
            return this;
        }

        @Override
        public boolean take(int itemId) {
            if (!this.inventory.containsKey(itemId)) return false;
            this.held.add(itemId);
            return true;
        }

        @Override
        public void giveBack(int itemId) {
            this.held.remove(Integer.valueOf(itemId));
        }

        @Override
        public OfferedItem describe(int itemId) {
            return this.inventory.get(itemId);
        }

        List<Integer> held() {
            return this.held;
        }
    }

    private static Term furni(int baseItemId, int amount) {
        return Term.furni(InteractionWiredContract.DIR_PAY, false, baseItemId, "", amount);
    }

    private static WiredTradingSession session(ContractRules rules, FakeVault vault, int credits) {
        return new WiredTradingSession(rules, vault, type -> type == CREDITS ? credits : 0, TIMEOUT, 0L);
    }

    private static ContractRules oneChair() {
        return ContractRules.of(List.of(List.of(furni(1389, 1))), List.of());
    }

    @Test
    void aSessionOpensReadyToTakeItems() {
        assertEquals(
                WiredTradingSession.STATE_ADDING_ITEMS,
                session(oneChair(), new FakeVault(), 0).getState());
    }

    @Test
    void offeringAnItemTakesItOutOfTheInventory() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);

        assertEquals(1, session.offerItems(List.of(101)));
        assertEquals(List.of(101), vault.held());
        assertEquals(1, session.getOfferedItems().size());
    }

    @Test
    void theSameItemCannotBePutOnTheTableTwice() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);

        session.offerItems(List.of(101));

        assertEquals(0, session.offerItems(List.of(101)));
        assertEquals(1, session.getOfferedItems().size());
    }

    @Test
    void anItemTheInventoryDoesNotHaveIsRefused() {
        WiredTradingSession session = session(oneChair(), new FakeVault(), 0);

        assertEquals(0, session.offerItems(List.of(999)));
    }

    @Test
    void acceptingIsRefusedUntilTheOfferCoversARule() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);

        assertFalse(session.canAccept());
        assertFalse(session.accept(1_000L));

        session.offerItems(List.of(101));

        assertTrue(session.canAccept());
        assertTrue(session.accept(1_000L));
        assertEquals(WiredTradingSession.STATE_COUNTDOWN, session.getState());
    }

    @Test
    void nothingCanBeSlippedOntoTheTableUnderAnAcceptance() {
        // The official window only offers items in the adding state, and refusing outright is the
        // safe reading: an acceptance can never come to cover more than the player agreed to.
        FakeVault vault = new FakeVault().add(101, 1389).add(102, 4242);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.offerItems(List.of(101));
        session.accept(1_000L);

        assertEquals(0, session.offerItems(List.of(102)));
        assertEquals(1, session.getOfferedItems().size());
        assertEquals(WiredTradingSession.STATE_COUNTDOWN, session.getState());
    }

    @Test
    void takingAnItemBackAlsoDropsTheAcceptance() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.offerItems(List.of(101));
        session.accept(1_000L);

        assertEquals(1, session.withdrawItems(List.of(101)));

        assertEquals(WiredTradingSession.STATE_ADDING_ITEMS, session.getState());
        assertTrue(vault.held().isEmpty());
    }

    @Test
    void confirmingTooSoonAfterAcceptingIsRefused() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.offerItems(List.of(101));
        session.accept(1_000L);

        assertFalse(session.confirm(1_500L).settled());
        assertFalse(session.readyToConfirm(1_500L));
        assertTrue(session.readyToConfirm(1_000L + WiredTradingSession.CONFIRM_DELAY_MILLIS));
    }

    @Test
    void aConfirmedOfferSettlesTheRuleItPaysFor() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.offerItems(List.of(101));
        session.accept(1_000L);

        Confirmation confirmation = session.confirm(1_000L + WiredTradingSession.CONFIRM_DELAY_MILLIS);

        assertTrue(confirmation.settled());
        assertEquals(0, confirmation.match().ruleIndex());
        assertEquals(List.of(101), confirmation.match().consumedItemIds());
        assertEquals(WiredTradingSession.STATE_CONFIRMED, session.getState());
    }

    @Test
    void aSecondConfirmationSettlesNothing() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.offerItems(List.of(101));
        session.accept(1_000L);
        long ready = 1_000L + WiredTradingSession.CONFIRM_DELAY_MILLIS;
        session.confirm(ready);

        assertFalse(session.confirm(ready).settled());
    }

    @Test
    void confirmingAfterTheOfferLapsedFailsOnTimeout() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.offerItems(List.of(101));
        session.accept(1_000L);
        session.readyToConfirm(1_000L + WiredTradingSession.CONFIRM_DELAY_MILLIS);

        Confirmation confirmation = session.confirm((TIMEOUT + 5) * 1000L);

        assertFalse(confirmation.settled());
        assertEquals(WiredTradingSession.FAILURE_TIMEOUT, confirmation.failureId());
    }

    @Test
    void closingHandsBackEverythingItStillHolds() {
        FakeVault vault = new FakeVault().add(101, 1389).add(102, 4242);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.offerItems(List.of(101, 102));

        assertEquals(List.of(101, 102), session.close());
        assertTrue(vault.held().isEmpty());
    }

    @Test
    void closingTwiceCannotHandTheSameItemBackTwice() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.offerItems(List.of(101));
        session.close();

        assertTrue(session.close().isEmpty());
    }

    @Test
    void consumedItemsAreNotHandedBackWhenTheSessionCloses() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.offerItems(List.of(101));
        session.accept(1_000L);
        Confirmation confirmation = session.confirm(1_000L + WiredTradingSession.CONFIRM_DELAY_MILLIS);

        session.consume(confirmation.match().consumedItemIds());

        assertTrue(session.close().isEmpty());
    }

    @Test
    void theClockCountsDownAndStopsAtZero() {
        WiredTradingSession session = session(oneChair(), new FakeVault(), 0);

        assertEquals(TIMEOUT, session.secondsLeft(0L));
        assertEquals(TIMEOUT - 10, session.secondsLeft(10_000L));
        assertEquals(0, session.secondsLeft((TIMEOUT + 30) * 1000L));
        assertTrue(session.hasExpired((TIMEOUT + 1) * 1000L));
    }

    @Test
    void aWalletOnlyContractNeedsNothingOnTheTable() {
        ContractRules rules = ContractRules.of(
                List.of(List.of(Term.currency(InteractionWiredContract.DIR_PAY, CREDITS, 5))), List.of());
        WiredTradingSession session = session(rules, new FakeVault(), 5);

        assertTrue(session.canAccept());
        assertTrue(session.accept(1_000L));
    }

    @Test
    void aClosedSessionAcceptsNothingFurther() {
        FakeVault vault = new FakeVault().add(101, 1389);
        WiredTradingSession session = session(oneChair(), vault, 0);
        session.close();

        assertEquals(0, session.offerItems(List.of(101)));
        assertFalse(session.accept(1_000L));
        assertFalse(session.confirm(9_000L).settled());
    }
}
