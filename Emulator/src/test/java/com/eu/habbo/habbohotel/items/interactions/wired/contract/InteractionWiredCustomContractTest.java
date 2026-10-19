package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The custom contract in AIR's form (addon 20, {@code class_4234}): one payment element and one reward element, each
 * coins or furni, a fixed amount or a variable. Contracts our older free-form editor saved keep working, and move to
 * the official form only when it says exactly the same.
 */
class InteractionWiredCustomContractTest {

    private static InteractionWiredCustomContract contract() {
        return new InteractionWiredCustomContract(4, 1, null, "", 0, 0);
    }

    private static WiredSettings settings(int[] params, int[] picks, int[] picks2, String... variableIds) {
        WiredSettings settings = new WiredSettings(params, "", picks, 0);
        settings.setFurniIds2(picks2);
        settings.setVariableIds(variableIds);
        return settings;
    }

    private static WiredContext context(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
    }

    private static HabboItem furni(int id, int baseItemId) {
        Item base = mock(Item.class);
        when(base.getId()).thenReturn(baseItemId);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getName()).thenReturn("chair");
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }

    @Test
    void onlyTheOfficialTenIntsAreTheOfficialForm() {
        assertTrue(InteractionWiredCustomContract.isOfficialParams(new int[] {1, 0, 0, 5, -10, 0, 0, 0, 1, -10}));
        assertFalse(InteractionWiredCustomContract.isOfficialParams(new int[] {-1, 1, 1, 0, -1, 0, 0, 5, 0}));
        assertFalse(
                InteractionWiredCustomContract.isOfficialParams(new int[] {3, 0, -1, 1, 0, -1, 2, 1, -1, 3}),
                "three flat terms of the oldest editor");
    }

    @Test
    void savesAndSendsBackTheOfficialForm() throws Exception {
        InteractionWiredCustomContract contract = contract();

        contract.saveData(
                settings(new int[] {1, 1, 0, 3, -10, 1, 0, 1, 1, -20}, new int[] {51}, new int[] {}, "", "77"), null);

        assertTrue(contract.isOfficialForm());
        assertArrayEquals(new int[] {1, 1, 0, 3, -10, 1, 0, 1, 1, -20}, contract.officialParams());
        assertArrayEquals(new String[] {"", "77"}, contract.officialVariableIds());
        assertArrayEquals(new int[] {100, 101}, contract.officialFurniSources());

        InteractionWiredCustomContract reloaded = contract();
        reloaded.applyWiredData(contract.getWiredData());
        assertTrue(reloaded.isOfficialForm());
        assertArrayEquals(contract.officialParams(), reloaded.officialParams());
        assertArrayEquals(new String[] {"", "77"}, reloaded.officialVariableIds());
    }

    @Test
    void anAmountFromAVariableNeedsTheVariable() {
        assertThrows(
                WiredSaveException.class,
                () -> contract()
                        .saveData(
                                settings(new int[] {1, 0, 1, 1, -10, 0, 0, 0, 1, -10}, new int[0], new int[0]), null));
    }

    @Test
    void theElementsBecomeThisRunsTerms() throws Exception {
        InteractionWiredCustomContract contract = contract();
        contract.saveData(
                settings(new int[] {1, 1, 0, 3, -10, 1, 0, 0, 250_000, -10}, new int[] {51}, new int[0]), null);
        Room room = mock(Room.class);
        HabboItem chair = furni(51, 900);
        when(room.getHabboItem(51)).thenReturn(chair);

        List<List<Term>> give = contract.giveRulesFor(context(room), room);
        List<Term> get = contract.getRuleFor(context(room), room);

        assertEquals(1, give.size());
        Term paid = give.get(0).get(0);
        assertTrue(paid.isFurni());
        assertEquals(900, paid.baseItemId);
        assertEquals(3, paid.amount);
        assertEquals(1, get.size());
        assertEquals(InteractionWiredContract.CURRENCY_CREDITS, get.get(0).currencyType);
        assertEquals(100_000, get.get(0).amount, "held to the official 100000");
    }

    @Test
    void aFurniElementWithoutFurniCannotRun() throws Exception {
        InteractionWiredCustomContract contract = contract();
        contract.saveData(settings(new int[] {1, 1, 0, 3, -10, 0, 0, 0, 1, -10}, new int[] {51}, new int[0]), null);
        Room room = mock(Room.class);

        assertNull(contract.giveRulesFor(context(room), room), "the picked furni is gone");
        assertEquals(List.of(), contract.getRuleFor(context(room), room), "the reward is off");
    }

    @Test
    void aFreeFormContractTheOfficialFormCanSayOpensInIt() {
        InteractionWiredCustomContract contract = contract();
        String stored = "{\"giveRules\":[[{\"direction\":0,\"currencyType\":-1,\"amount\":5}]],"
                + "\"getRule\":[{\"direction\":1,\"currencyType\":-1,\"amount\":2},"
                + "{\"direction\":1,\"currencyType\":-1,\"amount\":3}]}";
        contract.applyWiredData(stored);
        String reloaded = contract.getWiredData();

        assertTrue(contract.showsOfficialForm());
        assertFalse(contract.isOfficialForm(), "kept as stored until it is saved again");
        assertArrayEquals(new int[] {1, 0, 0, 5, -10, 1, 0, 0, 5, -10}, contract.officialParams());
        assertArrayEquals(new int[] {100, 101}, contract.officialFurniSources());
        assertEquals(reloaded, contract.getWiredData());
        assertEquals(5, contract.giveRulesFor(null, null).get(0).get(0).amount);
        assertEquals(
                5,
                contract.getRuleFor(null, null).stream()
                        .mapToInt(term -> term.amount)
                        .sum(),
                "it runs on its stored rules, which pay the same");
    }

    @Test
    void aFreeFormContractTheOfficialFormCannotSayKeepsItsShapeAndEditor() {
        String[] unrepresentable = {
            "{\"giveRules\":[[{\"direction\":0,\"currencyType\":-1,\"amount\":5}],"
                    + "[{\"direction\":0,\"currencyType\":-1,\"amount\":6}]]}",
            "{\"giveRules\":[[{\"direction\":0,\"currencyType\":5,\"amount\":5}]]}",
            "{\"giveRules\":[[{\"direction\":0,\"kind\":1,\"baseItemId\":10,\"amount\":1}]]}",
            "{\"giveRules\":[[{\"direction\":0,\"currencyType\":-1,\"amount\":5},"
                    + "{\"direction\":0,\"kind\":1,\"baseItemId\":10,\"amount\":1}]]}",
            "{\"giveRules\":[[{\"direction\":0,\"currencyType\":-1,\"amount\":5}]],\"chestIds\":[42]}"
        };
        for (String stored : unrepresentable) {
            InteractionWiredCustomContract contract = contract();
            contract.applyWiredData(stored);

            assertFalse(contract.showsOfficialForm(), stored);
            assertEquals(-1, contract.buildRuleParams()[0], "the free-form editor's params");
            assertArrayEquals(new int[0], contract.officialFurniSources());
        }
    }

    @Test
    void aFreeFormContractWhoseChestsMovedOpensInTheOfficialForm() {
        InteractionWiredCustomContract contract = contract();
        contract.applyWiredData(
                "{\"giveRules\":[[{\"direction\":0,\"currencyType\":-1,\"amount\":5}]],\"chestIds\":[42]}");
        assertTrue(contract.editableOnceChestsMove(null));

        contract.getChestIds().clear();

        assertTrue(contract.showsOfficialForm());
    }

    @Test
    void theFreeFormEditorStillSaves() throws Exception {
        InteractionWiredCustomContract contract = contract();
        contract.saveData(
                settings(new int[] {-1, 2, 1, 0, -1, 0, 0, 5, 1, 0, 5, 0, 0, 9, 0}, new int[0], new int[0]), null);

        assertFalse(contract.isOfficialForm());
        assertEquals(2, contract.getGiveRules().size());
        assertEquals(9, contract.getGiveRules().get(1).get(0).amount);
    }
}
