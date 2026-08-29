package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradeSettlement.Plan;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Settlement is the moment real property changes hands, so the decision to go ahead is made in full
 * before anything moves. These pin the refusals: they are what stand between a player and paying into
 * a reward that was never there.
 */
class WiredTradeSettlementTest {

    private static final int CREDITS = -1;
    private static final int CHAIR = 1389;

    private static IntUnaryOperator wallet(Map<Integer, Integer> balances) {
        return type -> balances.getOrDefault(type, 0);
    }

    private static Term coins(int direction, int amount) {
        return Term.currency(direction, CREDITS, amount);
    }

    private static Term furni(int direction, int baseItemId, int amount) {
        return Term.furni(direction, false, baseItemId, "", amount);
    }

    private static ChestStorage chestWith(int kind, int type, int quantity) {
        ChestStorage storage = new ChestStorage();
        storage.add(kind, type, quantity);
        return storage;
    }

    @Test
    void aPlanCarriesTheExactItemsThatWereOffered() {
        Plan plan = WiredTradeSettlement.plan(
                wallet(Map.of()),
                List.of(furni(InteractionWiredContract.DIR_PAY, CHAIR, 2)),
                List.of(101, 102),
                List.of(),
                null);

        assertNotNull(plan);
        assertEquals(List.of(101, 102), plan.itemsToTake());
    }

    @Test
    void currencyIsPlannedOnTheRuleTotal() {
        Plan plan = WiredTradeSettlement.plan(
                wallet(Map.of(CREDITS, 10)),
                List.of(coins(InteractionWiredContract.DIR_PAY, 5), coins(InteractionWiredContract.DIR_PAY, 5)),
                List.of(),
                List.of(),
                null);

        assertNotNull(plan);
        assertEquals(1, plan.currencyToTake().size());
        assertEquals(10, plan.currencyToTake().get(0).amount);
    }

    @Test
    void aWalletThatCannotCoverTheTotalRefusesTheWholePlan() {
        assertNull(WiredTradeSettlement.plan(
                wallet(Map.of(CREDITS, 9)),
                List.of(coins(InteractionWiredContract.DIR_PAY, 5), coins(InteractionWiredContract.DIR_PAY, 5)),
                List.of(),
                List.of(),
                null));
    }

    @Test
    void aChestThatCannotCoverTheRewardRefusesBeforeAnythingIsTaken() {
        ChestStorage nearlyEmpty = chestWith(ChestStorage.KIND_CURRENCY, CREDITS, 3);

        assertNull(WiredTradeSettlement.plan(
                wallet(Map.of(CREDITS, 100)),
                List.of(coins(InteractionWiredContract.DIR_PAY, 1)),
                List.of(),
                List.of(coins(InteractionWiredContract.DIR_RECEIVE, 5)),
                nearlyEmpty));
    }

    @Test
    void aChestThatCanCoverTheRewardGoesAhead() {
        ChestStorage stocked = chestWith(ChestStorage.KIND_CURRENCY, CREDITS, 50);

        Plan plan = WiredTradeSettlement.plan(
                wallet(Map.of(CREDITS, 100)),
                List.of(coins(InteractionWiredContract.DIR_PAY, 1)),
                List.of(),
                List.of(coins(InteractionWiredContract.DIR_RECEIVE, 5)),
                stocked);

        assertNotNull(plan);
        assertEquals(1, plan.rewardToGive().size());
    }

    @Test
    void aRewardAskedForTwiceMustBeCoveredInFullByTheChest() {
        // Two terms of three from a chest holding five: checking them one at a time would pass, and
        // the second half of the reward would silently never arrive.
        ChestStorage stocked = chestWith(ChestStorage.KIND_FURNI, CHAIR, 5);

        assertNull(WiredTradeSettlement.plan(
                wallet(Map.of()),
                List.of(),
                List.of(),
                List.of(
                        furni(InteractionWiredContract.DIR_RECEIVE, CHAIR, 3),
                        furni(InteractionWiredContract.DIR_RECEIVE, CHAIR, 3)),
                stocked));
    }

    @Test
    void aContractWithoutAChestMintsAndSoIsNeverShort() {
        Plan plan = WiredTradeSettlement.plan(
                wallet(Map.of()),
                List.of(),
                List.of(),
                List.of(furni(InteractionWiredContract.DIR_RECEIVE, CHAIR, 999)),
                null);

        assertNotNull(plan);
    }

    @Test
    void aMissingRuleOrRewardIsRefusedRatherThanTreatedAsEmpty() {
        assertNull(WiredTradeSettlement.plan(wallet(Map.of()), null, List.of(), List.of(), null));
        assertNull(WiredTradeSettlement.plan(wallet(Map.of()), List.of(), null, List.of(), null));
        assertNull(WiredTradeSettlement.plan(wallet(Map.of()), List.of(), List.of(), null, null));
    }
}
