package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A contract could only ever state one set of terms, all of which had to be met. It can now state
 * alternatives — "two chairs and five coins, or one sofa" — which is the grammar the official
 * requirements bubble renders.
 *
 * <p>The risk in adding that is silent: a contract already standing in somebody's room must keep
 * costing exactly what its owner set, and a payload with no alternatives in it must not be read as
 * having none. These pin both directions of that.
 */
class InteractionWiredContractRulesTest {

    /** The abstract base is all that is under test; the subclasses differ only by dialog code. */
    private static final class TestContract extends InteractionWiredContract {
        private TestContract() {
            super(0, 0, null, "", 0, 0);
        }

        @Override
        protected int contractCode() {
            return 110;
        }
    }

    private static TestContract loadedFrom(String json) {
        TestContract contract = new TestContract();
        contract.applyWiredData(json);
        return contract;
    }

    @Test
    void aContractWrittenBeforeAlternativesBecomesASingleAlternative() {
        String legacy = "{\"terms\":[{\"direction\":0,\"currencyType\":-1,\"amount\":5},"
                + "{\"direction\":0,\"currencyType\":0,\"amount\":2},"
                + "{\"direction\":1,\"currencyType\":-1,\"amount\":9}],\"chestIds\":[42]}";

        TestContract contract = loadedFrom(legacy);

        assertEquals(1, contract.getGiveRules().size());
        assertEquals(2, contract.getGiveRules().get(0).size());
        assertEquals(1, contract.getGetRule().size());
        assertEquals(9, contract.getGetRule().get(0).amount);
        assertEquals(List.of(42), contract.getChestIds());
    }

    @Test
    void aLegacyTermWithoutAKindReadsAsCurrency() {
        TestContract contract = loadedFrom("{\"terms\":[{\"direction\":0,\"currencyType\":-1,\"amount\":5}]}");

        assertTrue(contract.getGiveRules().get(0).get(0).isCurrency());
        assertFalse(contract.getGiveRules().get(0).get(0).isFurni());
    }

    @Test
    void alternativesSurviveBeingWrittenAndReadBack() {
        TestContract original = new TestContract();
        original.giveRules.add(List.of(Term.furni(0, false, 1389, "", 2), Term.currency(0, -1, 5)));
        original.giveRules.add(List.of(Term.furni(0, false, 4242, "", 1)));
        original.getRule.add(Term.currency(1, -1, 9));
        original.rebuildFlatTerms();

        TestContract reloaded = loadedFrom(original.getWiredData());

        assertEquals(2, reloaded.getGiveRules().size());
        assertEquals(2, reloaded.getGiveRules().get(0).size());
        assertEquals(4242, reloaded.getGiveRules().get(1).get(0).baseItemId);
        assertTrue(reloaded.getGiveRules().get(1).get(0).isFurni());
        assertEquals(9, reloaded.getGetRule().get(0).amount);
    }

    @Test
    void theFlattenedViewStaysInStepWithTheRules() {
        // The older instant path reads getTerms(); it must never disagree with the grammar about what
        // the contract costs.
        TestContract contract = new TestContract();
        contract.giveRules.add(List.of(Term.currency(0, -1, 5)));
        contract.giveRules.add(List.of(Term.currency(0, 0, 3)));
        contract.getRule.add(Term.currency(1, -1, 1));
        contract.rebuildFlatTerms();

        assertEquals(3, contract.getTerms().size());
    }

    @Test
    void aPayloadWrittenWithAlternativesIsStillReadableByAnOlderBuild() {
        // The flat list is written alongside the grammar for exactly this: an older jar looking at
        // this payload finds the contract it understands rather than an empty one.
        TestContract contract = new TestContract();
        contract.giveRules.add(List.of(Term.currency(0, -1, 5)));
        contract.rebuildFlatTerms();

        assertTrue(contract.getWiredData().contains("\"terms\""));
        assertTrue(contract.getWiredData().contains("\"giveRules\""));
    }

    @Test
    void anEmptyOrMalformedPayloadLeavesAContractThatAsksForNothing() {
        assertTrue(loadedFrom(null).getTerms().isEmpty());
        assertTrue(loadedFrom("").getTerms().isEmpty());
        assertTrue(loadedFrom("not json").getTerms().isEmpty());
    }

    @Test
    void aZeroAmountTermIsDroppedRatherThanStored() {
        TestContract contract = loadedFrom("{\"giveRules\":[[{\"direction\":0,\"currencyType\":-1,\"amount\":0},"
                + "{\"direction\":0,\"currencyType\":-1,\"amount\":4}]]}");

        assertEquals(1, contract.getGiveRules().get(0).size());
        assertEquals(4, contract.getGiveRules().get(0).get(0).amount);
    }

    @Test
    void readsTheExactArrayTheDialogWrites() {
        // Pinned identically in the client's contractTermWire.test.ts. The dialog and the contract
        // used to disagree about the shape of a term, and the only symptom was a saved contract
        // coming back empty -- so the agreement is stated in both languages, not just one.
        int[] fromDialog = {
            InteractionWiredContract.RULES_FORMAT,
            2,
            2,
            InteractionWiredContract.KIND_FURNI,
            0,
            0,
            1389,
            2,
            InteractionWiredContract.KIND_CURRENCY,
            -1,
            0,
            0,
            5,
            1,
            InteractionWiredContract.KIND_FURNI,
            0,
            0,
            4242,
            1,
            1,
            InteractionWiredContract.KIND_CURRENCY,
            0,
            0,
            0,
            9,
        };

        TestContract contract = new TestContract();
        contract.readRules(fromDialog);

        assertEquals(2, contract.getGiveRules().size());
        assertEquals(2, contract.getGiveRules().get(0).size());
        assertEquals(1389, contract.getGiveRules().get(0).get(0).baseItemId);
        assertEquals(2, contract.getGiveRules().get(0).get(0).amount);
        assertEquals(5, contract.getGiveRules().get(0).get(1).amount);
        assertEquals(4242, contract.getGiveRules().get(1).get(0).baseItemId);
        assertEquals(1, contract.getGetRule().size());
        assertEquals(9, contract.getGetRule().get(0).amount);
    }

    @Test
    void writesBackTheSameArrayItRead() {
        int[] fromDialog = {
            InteractionWiredContract.RULES_FORMAT,
            2,
            2,
            InteractionWiredContract.KIND_FURNI,
            0,
            0,
            1389,
            2,
            InteractionWiredContract.KIND_CURRENCY,
            -1,
            0,
            0,
            5,
            1,
            InteractionWiredContract.KIND_FURNI,
            0,
            0,
            4242,
            1,
            1,
            InteractionWiredContract.KIND_CURRENCY,
            0,
            0,
            0,
            9,
        };

        TestContract contract = new TestContract();
        contract.readRules(fromDialog);

        assertArrayEquals(fromDialog, contract.buildRuleParams());
    }
}
