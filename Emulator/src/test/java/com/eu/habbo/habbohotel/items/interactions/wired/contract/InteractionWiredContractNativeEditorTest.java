package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.WiredNativeContract.Node;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The official contract editor works on the contracts we already store. What it cannot show must keep the wired
 * editor, what it saves must respect its limits, and a contract it never touched must keep its payload byte for byte.
 */
class InteractionWiredContractNativeEditorTest {

    /** Base item 10 is a chair (type id 1389), 11 a twin of it, 12 untradeable, 20 a wall poster (type id 4001). */
    private static final ContractFurniCatalog CATALOG = new ContractFurniCatalog() {
        private final Map<Integer, Integer> typeIds = Map.of(10, 1389, 11, 1389, 12, 77, 20, 4001);

        @Override
        public int typeIdOf(int baseItemId) {
            return this.typeIds.getOrDefault(baseItemId, 0);
        }

        @Override
        public int baseItemIdOf(boolean wall, int typeId, int preferredBaseItemId) {
            if (preferredBaseItemId > 0 && typeIdOf(preferredBaseItemId) == typeId) return preferredBaseItemId;
            if (!wall && typeId == 1389) return 10;
            if (!wall && typeId == 77) return 12;
            if (wall && typeId == 4001) return 20;
            return 0;
        }

        @Override
        public boolean allowedInContract(int baseItemId) {
            return baseItemId != 12;
        }
    };

    private static InteractionWiredContract payment() {
        return new InteractionWiredContractPayment(1, 1, null, "", 0, 0);
    }

    private static InteractionWiredContract trade() {
        return new InteractionWiredContractTrade(2, 1, null, "", 0, 0);
    }

    private static InteractionWiredContract reward() {
        return new InteractionWiredContractReward(3, 1, null, "", 0, 0);
    }

    private static WiredNativeContract nativePayment(int mode, List<List<Node>> give) {
        return new WiredNativeContract(
                1, WiredNativeContract.TYPE_PAYMENT, give, null, mode, "A cookie", "games", 11, false, "");
    }

    @Test
    void anOldContractShowsInTheOfficialEditor() {
        InteractionWiredContract contract = payment();
        contract.applyWiredData("{\"terms\":[{\"direction\":0,\"currencyType\":-1,\"amount\":5}]}");

        WiredNativeContract shown = contract.toNative(99, CATALOG);

        assertNotNull(shown);
        assertEquals(99, shown.contractId());
        assertEquals(List.of(List.of(Node.coins(5))), shown.give());
        assertNull(shown.get());
        assertEquals(WiredNativeContract.PAYMENT_MODE_SPECIFIC, shown.paymentMode());
        assertTrue(contract.opensNativeEditor(CATALOG));
    }

    @Test
    void whatTheOfficialEditorCannotShowKeepsTheWiredEditor() {
        InteractionWiredContract duckets = payment();
        duckets.applyWiredData("{\"terms\":[{\"direction\":0,\"currencyType\":0,\"amount\":5}]}");
        assertNull(duckets.toNative(1, CATALOG), "duckets are not the editor's coins");

        InteractionWiredContract chests = payment();
        chests.applyWiredData("{\"terms\":[{\"direction\":0,\"currencyType\":-1,\"amount\":5}],\"chestIds\":[42]}");
        assertFalse(chests.opensNativeEditor(CATALOG), "linked chests are only shown by the wired editor");

        InteractionWiredContract paymentWithReward = payment();
        paymentWithReward.applyWiredData(
                "{\"giveRules\":[[]],\"getRule\":[{\"direction\":1,\"currencyType\":-1,\"amount\":5}]}");
        assertNull(paymentWithReward.toNative(1, CATALOG), "a payment has no reward side in the official editor");

        InteractionWiredContract custom = new InteractionWiredCustomContract(4, 1, null, "", 0, 0);
        assertFalse(custom.supportsNativeEditor(), "the custom contract is a stack add-on");
        assertNull(custom.toNative(1, CATALOG));
    }

    @Test
    void furniGoesOutAsItsTypeIdAndComesBackAsTheSameBaseItem() {
        InteractionWiredContract contract = trade();
        contract.applyWiredData(
                "{\"giveRules\":[[{\"direction\":0,\"kind\":1,\"baseItemId\":11,\"amount\":2}]],"
                        + "\"getRule\":[{\"direction\":1,\"kind\":1,\"wallItem\":true,\"baseItemId\":20,\"posterId\":\"9\",\"amount\":1}]}");

        WiredNativeContract shown = contract.toNative(2, CATALOG);
        assertEquals(List.of(List.of(Node.furni(2, false, 1389, ""))), shown.give());
        assertEquals(List.of(Node.furni(1, true, 4001, "9")), shown.get());

        assertTrue(contract.applyNative(shown, CATALOG));
        Term chair = contract.getGiveRules().get(0).get(0);
        assertEquals(11, chair.baseItemId, "the twin the owner picked is kept");
        assertEquals("9", contract.getGetRule().get(0).posterId());
    }

    @Test
    void savesPaymentOptionsAndKeepsThemAcrossAReload() {
        InteractionWiredContract contract = payment();

        assertTrue(contract.applyNative(nativePayment(WiredNativeContract.PAYMENT_MODE_DONATION, List.of()), CATALOG));
        assertTrue(contract.isDonation());

        InteractionWiredContract reloaded = payment();
        reloaded.applyWiredData(contract.getWiredData());
        assertEquals(WiredNativeContract.PAYMENT_MODE_DONATION, reloaded.getPaymentMode());
        assertEquals("A cookie", reloaded.getReceiveText());
        assertEquals("games", reloaded.getLayoutType());
    }

    @Test
    void savesRewardOptions() {
        InteractionWiredContract contract = reward();
        WiredNativeContract saved = new WiredNativeContract(
                3,
                WiredNativeContract.TYPE_REWARD,
                null,
                List.of(Node.coins(9)),
                1,
                "",
                "generic",
                13,
                true,
                "Well done");

        assertTrue(contract.applyNative(saved, CATALOG));

        InteractionWiredContract reloaded = reward();
        reloaded.applyWiredData(contract.getWiredData());
        assertEquals(13, reloaded.getRewardCategory());
        assertTrue(reloaded.isShowDialog());
        assertEquals("Well done", reloaded.getRewardText());
        assertEquals(9, reloaded.getGetRule().get(0).amount);
        assertEquals(
                InteractionWiredContract.CURRENCY_CREDITS, reloaded.getGetRule().get(0).currencyType);
    }

    @Test
    void anUntouchedContractKeepsItsPayloadByteForByte() {
        String stored = "{\"terms\":[{\"direction\":0,\"currencyType\":-1,\"amount\":5,\"kind\":0,\"wallItem\":false,"
                + "\"baseItemId\":0}],\"chestIds\":[],\"giveRules\":[[{\"direction\":0,\"currencyType\":-1,\"amount\":5,"
                + "\"kind\":0,\"wallItem\":false,\"baseItemId\":0}]],\"getRule\":[]}";
        InteractionWiredContract contract = payment();
        contract.applyWiredData(stored);

        assertEquals(stored, contract.getWiredData());
    }

    @Test
    void refusesWhatTheOfficialEditorWouldNotAllow() {
        List<List<Node>> one = List.of(List.of(Node.coins(1)));
        List<WiredNativeContract> refused = List.of(
                nativePayment(WiredNativeContract.PAYMENT_MODE_SPECIFIC, List.of()),
                nativePayment(2, one),
                nativePayment(WiredNativeContract.PAYMENT_MODE_SPECIFIC, List.of(List.of(Node.coins(0)))),
                nativePayment(WiredNativeContract.PAYMENT_MODE_SPECIFIC, List.of(List.of(Node.coins(100_001)))),
                nativePayment(
                        WiredNativeContract.PAYMENT_MODE_SPECIFIC, List.of(List.of(Node.furni(501, false, 1389, "")))),
                nativePayment(
                        WiredNativeContract.PAYMENT_MODE_SPECIFIC, List.of(List.of(Node.furni(1, false, 77, "")))),
                nativePayment(
                        WiredNativeContract.PAYMENT_MODE_SPECIFIC, List.of(List.of(Node.furni(1, false, 5555, "")))),
                nativePayment(WiredNativeContract.PAYMENT_MODE_SPECIFIC, List.of(List.of())),
                new WiredNativeContract(1, 0, one, List.of(Node.coins(1)), 1, "", "generic", 11, false, ""),
                new WiredNativeContract(1, 0, one, null, 1, "", "unknown", 11, false, ""),
                new WiredNativeContract(1, 0, one, null, 1, "x".repeat(61), "generic", 11, false, ""),
                new WiredNativeContract(
                        1,
                        WiredNativeContract.TYPE_REWARD,
                        null,
                        List.of(Node.coins(1)),
                        1,
                        "",
                        "generic",
                        11,
                        false,
                        ""));

        for (WiredNativeContract contract : refused) {
            InteractionWiredContract target = payment();
            target.applyWiredData("{\"terms\":[{\"direction\":0,\"currencyType\":-1,\"amount\":5}]}");
            String before = target.getWiredData();

            assertFalse(target.applyNative(contract, CATALOG), contract.toString());
            assertEquals(before, target.getWiredData(), "a refused save changes nothing");
        }

        assertFalse(reward().applyNative(
                        new WiredNativeContract(3, 2, null, List.of(Node.coins(1)), 1, "", "generic", 12, false, ""),
                        CATALOG));
        assertFalse(trade().applyNative(
                        new WiredNativeContract(
                                2, 1, List.of(), List.of(Node.coins(1)), 1, "", "generic", 11, false, ""),
                        CATALOG));
    }

    @Test
    void aSaveKeepsTheContractsChestLinks() {
        InteractionWiredContract contract = payment();
        contract.applyWiredData("{\"terms\":[{\"direction\":0,\"currencyType\":-1,\"amount\":5}],\"chestIds\":[42]}");

        assertTrue(contract.applyNative(
                nativePayment(WiredNativeContract.PAYMENT_MODE_SPECIFIC, List.of(List.of(Node.coins(8)))), CATALOG));
        assertEquals(List.of(42), contract.getChestIds());
        assertEquals(8, contract.getGiveRules().get(0).get(0).amount);
    }
}
