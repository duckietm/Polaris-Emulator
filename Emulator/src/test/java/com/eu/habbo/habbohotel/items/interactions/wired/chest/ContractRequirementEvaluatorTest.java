package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.Match;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.OfferedItem;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * What a player puts on the table has to be judged before anything is taken from them, so these pin
 * the decision itself: which alternative is paid, exactly which items pay for it, and what the window
 * should report as still missing.
 */
class ContractRequirementEvaluatorTest {

    private static final int CREDITS = -1;

    private static IntUnaryOperator wallet(Map<Integer, Integer> balances) {
        return type -> balances.getOrDefault(type, 0);
    }

    private static Term coins(int amount) {
        return Term.currency(InteractionWiredContract.DIR_PAY, CREDITS, amount);
    }

    private static Term furni(int baseItemId, int amount) {
        return Term.furni(InteractionWiredContract.DIR_PAY, false, baseItemId, "", amount);
    }

    private static OfferedItem offered(int itemId, int baseItemId) {
        return new OfferedItem(itemId, false, baseItemId);
    }

    @Test
    void aRuleIsPaidByTheOfferedFurniAndTheWallet() {
        ContractRules rules = ContractRules.of(List.of(List.of(furni(1389, 2), coins(5))), List.of());

        Match match = ContractRequirementEvaluator.firstSatisfied(
                rules, wallet(Map.of(CREDITS, 10)), List.of(offered(101, 1389), offered(102, 1389)));

        assertTrue(match.satisfied());
        assertEquals(0, match.ruleIndex());
        assertEquals(List.of(101, 102), match.consumedItemIds());
    }

    @Test
    void oneItemCannotPayTwoTermsAskingForTheSameFurni() {
        ContractRules rules = ContractRules.of(List.of(List.of(furni(1389, 1), furni(1389, 1))), List.of());

        Match match = ContractRequirementEvaluator.firstSatisfied(rules, wallet(Map.of()), List.of(offered(101, 1389)));

        assertFalse(match.satisfied());
        assertEquals(1, match.missing().size());
        assertEquals(1, match.missing().get(0).amount);
    }

    @Test
    void currencyIsSettledOnTheRuleTotalNotTermByTerm() {
        ContractRules rules = ContractRules.of(List.of(List.of(coins(5), coins(5))), List.of());

        assertFalse(ContractRequirementEvaluator.firstSatisfied(rules, wallet(Map.of(CREDITS, 5)), List.of())
                .satisfied());
        assertTrue(ContractRequirementEvaluator.firstSatisfied(rules, wallet(Map.of(CREDITS, 10)), List.of())
                .satisfied());
    }

    @Test
    void theFirstAffordableAlternativeWins() {
        ContractRules rules = ContractRules.of(List.of(List.of(furni(4242, 1)), List.of(coins(5))), List.of());

        Match match = ContractRequirementEvaluator.firstSatisfied(rules, wallet(Map.of(CREDITS, 5)), List.of());

        assertTrue(match.satisfied());
        assertEquals(1, match.ruleIndex());
        assertTrue(match.consumedItemIds().isEmpty());
    }

    @Test
    void anAlternativeNeverClaimsItemsWhenItIsNotSatisfied() {
        // The furni is offered but the coins are short: nothing may be marked as consumed, or the
        // caller could take the item for a rule that was never paid.
        ContractRules rules = ContractRules.of(List.of(List.of(furni(1389, 1), coins(50))), List.of());

        Match match = ContractRequirementEvaluator.firstSatisfied(
                rules, wallet(Map.of(CREDITS, 1)), List.of(offered(101, 1389)));

        assertFalse(match.satisfied());
        assertTrue(match.consumedItemIds().isEmpty());
    }

    @Test
    void theShortfallReportsTheAlternativeThePlayerIsNearestTo() {
        // Option 0 needs three things the player lacks, option 1 needs one. The window should nag
        // about the close one.
        ContractRules rules = ContractRules.of(
                List.of(List.of(furni(1, 1), furni(2, 1), furni(3, 1)), List.of(furni(9, 1))), List.of());

        Match match = ContractRequirementEvaluator.firstSatisfied(rules, wallet(Map.of()), List.of());

        assertFalse(match.satisfied());
        assertEquals(1, match.missing().size());
        assertEquals(9, match.missing().get(0).baseItemId);
    }

    @Test
    void aWallItemNeverPaysForAFloorTerm() {
        ContractRules rules = ContractRules.of(List.of(List.of(furni(77, 1))), List.of());

        Match match = ContractRequirementEvaluator.firstSatisfied(
                rules, wallet(Map.of()), List.of(new OfferedItem(101, true, 77)));

        assertFalse(match.satisfied());
    }

    @Test
    void aContractThatAsksForNothingIsSatisfiedByAnEmptyTable() {
        ContractRules rules = ContractRules.of(List.of(), List.of());

        Match match = ContractRequirementEvaluator.firstSatisfied(rules, wallet(Map.of()), List.of());

        assertTrue(match.satisfied());
        assertTrue(match.consumedItemIds().isEmpty());
    }

    @Test
    void surplusOfferedItemsAreLeftAlone() {
        ContractRules rules = ContractRules.of(List.of(List.of(furni(1389, 1))), List.of());

        Match match = ContractRequirementEvaluator.firstSatisfied(
                rules, wallet(Map.of()), List.of(offered(101, 1389), offered(102, 1389), offered(103, 4242)));

        assertTrue(match.satisfied());
        assertEquals(List.of(101), match.consumedItemIds());
    }
}
