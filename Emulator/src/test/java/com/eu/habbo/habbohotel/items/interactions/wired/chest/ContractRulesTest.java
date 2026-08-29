package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A contract used to be one flat list of terms that all had to be met. The official grammar is richer:
 * the give side is a set of alternatives, and each alternative is a set of terms that must all be met.
 *
 * <p>These pin the two things that could go wrong when adding that: a contract saved before the
 * grammar existed must keep meaning what it meant, and a hand-edited payload must not be able to make
 * a contract cheaper than its owner set it.
 */
class ContractRulesTest {

    private static ContractTerm pay(int currencyType, int amount) {
        return ContractTerm.currency(ContractTerm.DIR_PAY, currencyType, amount);
    }

    private static ContractTerm payFurni(int baseItemId, int amount) {
        return ContractTerm.furni(ContractTerm.DIR_PAY, false, baseItemId, "", amount);
    }

    private static ContractTerm receive(int currencyType, int amount) {
        return ContractTerm.currency(ContractTerm.DIR_RECEIVE, currencyType, amount);
    }

    @Test
    void aContractSavedBeforeAlternativesExistedReadsAsOneAlternative() {
        int[] legacy = ContractTerm.serialize(List.of(payFurni(1389, 2), pay(-1, 5), receive(0, 3)));

        ContractRules rules = ContractRules.parse(legacy, "");

        assertEquals(1, rules.giveRules().size());
        assertEquals(2, rules.giveRules().get(0).size());
        assertEquals(1, rules.getRule().size());
        assertEquals(3, rules.getRule().get(0).amount);
    }

    @Test
    void alternativesSurviveTheRoundTrip() {
        ContractRules saved = ContractRules.of(
                List.of(List.of(payFurni(1389, 2), pay(-1, 5)), List.of(payFurni(4242, 1))), List.of(receive(0, 3)));

        ContractRules reloaded = ContractRules.parse(saved.serialize(), saved.serializePosters());

        assertEquals(2, reloaded.giveRules().size());
        assertEquals(2, reloaded.giveRules().get(0).size());
        assertEquals(1, reloaded.giveRules().get(1).size());
        assertEquals(4242, reloaded.giveRules().get(1).get(0).baseItemId);
        assertEquals(1, reloaded.getRule().size());
    }

    @Test
    void wallPosterIdsStayWithTheirTermAcrossTheGrouping() {
        ContractRules saved = ContractRules.of(
                List.of(
                        List.of(ContractTerm.furni(ContractTerm.DIR_PAY, true, 77, "poster-a", 1)),
                        List.of(ContractTerm.furni(ContractTerm.DIR_PAY, true, 88, "poster-b", 1))),
                List.of());

        ContractRules reloaded = ContractRules.parse(saved.serialize(), saved.serializePosters());

        assertEquals("poster-a", reloaded.giveRules().get(0).get(0).legacyPosterId);
        assertEquals("poster-b", reloaded.giveRules().get(1).get(0).legacyPosterId);
    }

    @Test
    void aPartitionThatUndercountsStillChargesEveryTerm() {
        // The tail claims a single one-term alternative while three PAY terms were written. Dropping
        // the surplus would hand the player a discount the owner never offered.
        int[] params = ContractTerm.serialize(List.of(payFurni(1, 1), payFurni(2, 1), payFurni(3, 1)));
        int[] tampered = new int[params.length + 3];
        System.arraycopy(params, 0, tampered, 0, params.length);
        tampered[params.length] = ContractRules.RULES_MARKER;
        tampered[params.length + 1] = 1;
        tampered[params.length + 2] = 1;

        ContractRules rules = ContractRules.parse(tampered, "");

        int total = rules.giveRules().stream().mapToInt(List::size).sum();
        assertEquals(3, total);
    }

    @Test
    void anImpossibleRuleCountFallsBackToOneAlternative() {
        int[] params = ContractTerm.serialize(List.of(payFurni(1, 1)));
        int[] tampered = new int[params.length + 2];
        System.arraycopy(params, 0, tampered, 0, params.length);
        tampered[params.length] = ContractRules.RULES_MARKER;
        tampered[params.length + 1] = ContractRules.MAX_RULES + 99;

        ContractRules rules = ContractRules.parse(tampered, "");

        assertEquals(1, rules.giveRules().size());
        assertEquals(1, rules.giveRules().get(0).size());
    }

    @Test
    void amountsAreClampedToTheOfficialCeilings() {
        ContractRules rules = ContractRules.of(
                List.of(List.of(pay(-1, ContractRules.MAX_COINS + 5000), payFurni(1389, ContractRules.MAX_FURNI + 40))),
                List.of());

        assertEquals(ContractRules.MAX_COINS, rules.giveRules().get(0).get(0).amount);
        assertEquals(ContractRules.MAX_FURNI, rules.giveRules().get(0).get(1).amount);
    }

    @Test
    void tooManyAlternativesAreCutRatherThanAccepted() {
        List<List<ContractTerm>> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < ContractRules.MAX_RULES + 4; i++) tooMany.add(List.of(payFurni(1000 + i, 1)));

        assertEquals(
                ContractRules.MAX_RULES,
                ContractRules.of(tooMany, List.of()).giveRules().size());
    }

    @Test
    void anEmptyContractReportsBothHalvesEmpty() {
        ContractRules rules = ContractRules.of(List.of(), List.of());

        assertTrue(rules.asksForNothing());
        assertTrue(rules.givesNothing());
    }

    @Test
    void aContractThatAsksForSomethingSaysSo() {
        ContractRules rules = ContractRules.of(List.of(List.of(pay(-1, 1))), List.of(receive(0, 1)));

        assertFalse(rules.asksForNothing());
        assertFalse(rules.givesNothing());
    }
}
