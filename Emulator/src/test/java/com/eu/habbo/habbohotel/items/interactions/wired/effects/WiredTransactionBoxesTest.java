package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractMultiplier;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContractPayment;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.ServerMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Initiate Transaction (47) and Cancel Transaction (48) on their official layouts, and rows saved before them. */
class WiredTransactionBoxesTest {

    private static WiredSettings settings(String[] variableIds, int... params) {
        WiredSettings settings = new WiredSettings(params, "", new int[0], 0);
        settings.setVariableIds(variableIds);
        return settings;
    }

    /** Initiate with its chests from a selector: a save with picked chests must pick one (require_chests). */
    private static WiredSettings initSettings(String[] variableIds, int... params) {
        WiredSettings settings = settings(variableIds, params);
        settings.setFurniSourceTypes(new int[] {200});
        return settings;
    }

    private static WiredEffectInitTransaction init() {
        return new WiredEffectInitTransaction(1, 1, base(), "", 0, 0);
    }

    @Test
    void initiateSavesAndSendsBackTheOfficialInts() throws Exception {
        WiredEffectInitTransaction box = init();

        box.saveData(initSettings(new String[] {"42"}, 1, 7, 1, -20, 1, 600), null);

        assertArrayEquals(
                new int[] {1, 7, 1, -20, 1, 600},
                WiredEffectProgressAchievementTest.body(box).params());
        assertArrayEquals(new String[] {"ctx:42"}, box.officialVariableIds());
        assertEquals(600, box.negotiationTimeout());
    }

    @Test
    void initiateHoldsItsIntsToTheOfficialRanges() throws Exception {
        WiredEffectInitTransaction box = init();

        box.saveData(initSettings(new String[0], 5, 9_000, 1, 3, 1, 5), null);

        // An unknown mode is normal, the multiplier at most 500, no variable without an id, the target global,
        // the timeout at least 30 seconds.
        assertArrayEquals(new int[] {0, 500, 0, -10, 1, 30}, box.officialParams());
        assertArrayEquals(new String[0], box.officialVariableIds());

        box.saveData(initSettings(new String[0], 2, 0, 0, -10, 0, 99_999), null);
        assertArrayEquals(new int[] {2, 1, 0, -10, 0, 3600}, box.officialParams());
        assertEquals(0, box.negotiationTimeout(), "no timeout: the negotiation never lapses");
    }

    @Test
    void initiateKeepsItsChestsAndContractsOnTheirOwnPickLists() throws Exception {
        WiredEffectInitTransaction box = init();
        WiredSettings settings = initSettings(new String[0], 0, 1, 0, -10, 0, 300);
        settings.setFurniIds(new int[] {11, 12});
        settings.setFurniIds2(new int[] {21});
        settings.setFurniSourceTypes(new int[] {200, 101, 200});
        settings.setUserSourceTypes(new int[] {0, 200});

        box.saveData(settings, null);

        assertArrayEquals(new int[] {21}, box.officialFurniIds2());
        assertArrayEquals(new int[] {200, 101, 200}, box.officialFurniSources());
        assertArrayEquals(new int[] {0, 200}, box.officialUserSources());

        WiredEffectInitTransaction reloaded = init();
        reloaded.loadWiredData(row(box.getWiredData()), null);
        assertArrayEquals(new int[] {21}, reloaded.officialFurniIds2());
        assertEquals(List.of(11, 12), toList(json(reloaded).getAsJsonArray("chestIds")));
        assertArrayEquals(box.officialParams(), reloaded.officialParams());
    }

    @Test
    void anInitiateRowFromBeforeRunsOnceWithTheOldTimeoutAndSplitsItsPicks() throws Exception {
        WiredEffectInitTransaction box = init();
        box.loadWiredData(row("{\"delay\":2,\"contractIds\":[5,6]}"), null);

        assertArrayEquals(new int[] {0, 1, 0, -10, 1, 120}, box.officialParams());
        assertEquals(2, box.getDelay());

        // Once the room is up, the chest among the old picks moves to the chest list.
        Room room = mock(Room.class);
        when(room.getHabboItem(5)).thenReturn(mock(InteractionWiredContract.class));
        when(room.getHabboItem(6)).thenReturn(mock(InteractionWiredChestCurrency.class));
        box.serializeWiredData(new ServerMessage(0), room);

        assertArrayEquals(new int[] {5}, box.officialFurniIds2());
        assertEquals(List.of(6), toList(json(box).getAsJsonArray("chestIds")));
    }

    @Test
    void theMultiplierComesFromItsModeAndItsVariable() throws Exception {
        WiredEffectInitTransaction box = init();
        Room room = mock(Room.class);
        RoomVariableManager variables = mock(RoomVariableManager.class);
        when(room.getRoomVariableManager()).thenReturn(variables);
        WiredContext ctx = WiredEffectTestFixtures.context(room);

        box.saveData(initSettings(new String[0], 0, 8, 0, -10, 0, 300), null);
        assertEquals(ContractMultiplier.NONE, box.multiplierFor(ctx, ctx.room()), "normal ignores the value");

        box.saveData(initSettings(new String[0], 1, 8, 0, -10, 0, 300), null);
        assertEquals(new ContractMultiplier(ContractMultiplier.MODE_FIXED, 8), box.multiplierFor(ctx, ctx.room()));
        assertEquals(8, WiredEffectInitTransaction.rewardRuns(box.multiplierFor(ctx, ctx.room())));

        // A variable the room does not have runs the contract once.
        box.saveData(initSettings(new String[] {"9"}, 2, 1, 1, -10, 0, 300), null);
        assertEquals(new ContractMultiplier(ContractMultiplier.MODE_AUTO, 1), box.multiplierFor(ctx, ctx.room()));
        assertEquals(1, WiredEffectInitTransaction.rewardRuns(new ContractMultiplier(ContractMultiplier.MODE_AUTO, 9)));

        // The room variable's value, held to 1..500.
        when(variables.getDefinitionInfo(9)).thenReturn(mock(WiredVariableDefinitionInfo.class));
        when(variables.getCurrentValue(9)).thenReturn(4);
        assertEquals(new ContractMultiplier(ContractMultiplier.MODE_AUTO, 4), box.multiplierFor(ctx, room));
        when(variables.getCurrentValue(9)).thenReturn(700);
        assertEquals(500, box.multiplierFor(ctx, room).value());
    }

    @Test
    void cancelSavesItsMatchCriteriaAndContracts() throws Exception {
        WiredEffectCancelTransaction box = new WiredEffectCancelTransaction(1, 1, base(), "", 0, 0);

        box.saveData(settings(new String[0], 1), null);
        assertArrayEquals(
                new int[] {1}, WiredEffectProgressAchievementTest.body(box).params());
        assertNull(box.matchedContracts(null, mock(Room.class)), "any running transaction");

        WiredSettings specific = settings(new String[0], 0);
        specific.setFurniIds(new int[] {31});
        box.saveData(specific, null);

        WiredEffectCancelTransaction reloaded = new WiredEffectCancelTransaction(1, 1, base(), "", 0, 0);
        reloaded.loadWiredData(row(box.getWiredData()), null);
        assertArrayEquals(new int[] {100}, reloaded.officialFurniSources());
        assertEquals(0, json(reloaded).get("matchCriteria").getAsInt());
        assertEquals(List.of(31), toList(json(reloaded).getAsJsonArray("contractIds")));

        Room room = mock(Room.class);
        when(room.getHabboItem(31)).thenReturn(mock(InteractionWiredContract.class));
        when(room.getHabboItem(32)).thenReturn(mock(InteractionWiredChestCurrency.class));
        assertEquals(
                1,
                reloaded.matchedContracts(WiredEffectTestFixtures.context(room), room)
                        .size());
    }

    @Test
    void aCancelRowFromBeforeOpensOnTheSpecificContract() throws Exception {
        WiredEffectCancelTransaction box = new WiredEffectCancelTransaction(1, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"delay\":3}"), null);

        assertArrayEquals(
                new int[] {0}, WiredEffectProgressAchievementTest.body(box).params());
        assertEquals(3, box.getDelay());
    }

    private static WiredContext stackContext(Room room, IWiredEffect... effects) {
        WiredStack stack = new WiredStack(null, null, List.of(), List.of(effects));
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                stack,
                mock(WiredServices.class),
                new WiredState(20),
                null);
    }

    @Test
    void aCancelReplacesOnlyWhenAnInitiateIsStackedOnTopOfIt() throws Exception {
        Room room = mock(Room.class);
        WiredEffectCancelTransaction cancel = new WiredEffectCancelTransaction(1, 1, base(), "", 0, 0);
        WiredEffectInitTransaction initiate = init();

        assertTrue(cancel.replacedInSamePass(stackContext(room, cancel, initiate)), "initiate on top of cancel");
        assertFalse(cancel.replacedInSamePass(stackContext(room, initiate, cancel)), "initiate below the cancel");
        assertFalse(cancel.replacedInSamePass(stackContext(room, cancel)), "no initiate");
        assertFalse(cancel.replacedInSamePass(WiredEffectTestFixtures.context(room)), "no stack");

        WiredEffectInitTransaction delayed = init();
        delayed.loadWiredData(row("{\"delay\":2}"), null);
        assertFalse(cancel.replacedInSamePass(stackContext(room, cancel, delayed)), "a later pass opens a new window");
    }

    @Test
    void aRewardsCreditsLandInTheRewardContractsEarningsRow() {
        InteractionWiredContract reward = mock(InteractionWiredContract.class);
        when(reward.contractType()).thenReturn(InteractionWiredContract.TYPE_REWARD);
        when(reward.getRewardCategory()).thenReturn(13);
        InteractionWiredContract trade = mock(InteractionWiredContract.class);
        when(trade.contractType()).thenReturn(InteractionWiredContract.TYPE_TRADE);

        assertEquals(13, WiredEffectInitTransaction.earningsCategory(List.of(trade, reward)));
        assertEquals(11, WiredEffectInitTransaction.earningsCategory(List.of(trade)), "a trade counts as games");
    }

    private static WiredEffectInitTransaction initPicking(String wiredData) throws Exception {
        WiredEffectInitTransaction box = init();
        box.loadWiredData(row(wiredData), null);
        return box;
    }

    private static Room roomWith(InteractionWiredEffect... boxes) {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(special.getEffects()).thenReturn(new java.util.LinkedHashSet<>(List.of(boxes)));
        when(room.getRoomSpecialTypes()).thenReturn(special);
        return room;
    }

    @Test
    void aContractsOwnChestsMoveToTheInitiateBoxesThatPickIt() throws Exception {
        InteractionWiredContractPayment contract = new InteractionWiredContractPayment(5, 1, base(), "", 0, 0);
        contract.loadWiredData(
                row("{\"terms\":[{\"direction\":0,\"currencyType\":-1,\"amount\":5}],\"chestIds\":[42,43]}"), null);
        WiredEffectInitTransaction picking = initPicking("{\"contractIds\":[5],\"chestIds\":[43,44]}");
        WiredEffectInitTransaction other = initPicking("{\"contractIds\":[6],\"chestIds\":[]}");

        assertTrue(WiredEffectInitTransaction.adoptContractChests(roomWith(picking, other), contract));

        assertTrue(contract.getChestIds().isEmpty());
        assertEquals(List.of(43, 44, 42), picking.chestPicks(), "the box keeps its chests and gains the contract's");
        assertEquals(List.of(), other.chestPicks(), "a box that does not pick the contract is left alone");
    }

    @Test
    void aContractKeepsItsChestsWhenMovingThemCouldChangeAnything() throws Exception {
        String data = "{\"terms\":[{\"direction\":0,\"currencyType\":-1,\"amount\":5}],\"chestIds\":[42]}";
        InteractionWiredContractPayment contract = new InteractionWiredContractPayment(5, 1, base(), "", 0, 0);
        contract.loadWiredData(row(data), null);

        assertFalse(
                WiredEffectInitTransaction.adoptContractChests(roomWith(), contract), "no box picks it: nowhere to go");
        WiredEffectInitTransaction bySelector =
                initPicking("{\"contractIds\":[],\"chestIds\":[],\"furniSource2\":200}");
        WiredEffectInitTransaction picking = initPicking("{\"contractIds\":[5],\"chestIds\":[]}");
        assertFalse(
                WiredEffectInitTransaction.adoptContractChests(roomWith(picking, bySelector), contract),
                "a box choosing its contracts by selector may reach it too");
        WiredEffectInitTransaction chestsBySelector =
                initPicking("{\"contractIds\":[5],\"chestIds\":[],\"furniSource\":200}");
        assertFalse(
                WiredEffectInitTransaction.adoptContractChests(roomWith(chestsBySelector), contract),
                "a box that does not read its chest picks");

        assertEquals(List.of(42), contract.getChestIds(), "nothing changed");
        assertEquals(List.of(), picking.chestPicks());
    }

    private static List<Integer> toList(com.google.gson.JsonArray array) {
        List<Integer> out = new java.util.ArrayList<>();
        array.forEach(element -> out.add(element.getAsInt()));
        return out;
    }

    @Test
    void picksWithoutAChestAreRefusedAsOnAir() {
        Room room = mock(Room.class);
        when(room.getHabboItem(6)).thenReturn(mock(InteractionWiredChestCurrency.class));
        when(room.getHabboItem(5)).thenReturn(mock(InteractionWiredContract.class));

        assertFalse(WiredEffectInitTransaction.picksAChest(room, new int[0]));
        assertFalse(WiredEffectInitTransaction.picksAChest(room, new int[] {5}));
        assertTrue(WiredEffectInitTransaction.picksAChest(room, new int[] {5, 6}));
    }
}
