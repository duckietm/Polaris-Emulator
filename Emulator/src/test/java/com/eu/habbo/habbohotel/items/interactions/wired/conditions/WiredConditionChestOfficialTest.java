package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLegacyWireReader;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Chest Has Items (45) and Chest Has Item Types (46) as AIR's ChestHasAmount: ints [value, valueIsVariable, target
 * (0 furni, 1 user, -10 global, -20 context), comparison], the variable as the first variable id; 46 picks its item
 * types first and its chests second.
 */
class WiredConditionChestOfficialTest {
    private static final int CHEST = 1;
    private static final int TYPE_FURNI = 2;
    private static final int TYPE_BASE = 900;
    private static final int VARIABLE = 7;

    /** A room with a wired chest holding 2 coins and 3 furni, 2 of them of {@link #TYPE_BASE}. */
    private static Room room() {
        Room room = WiredConditionTestSupport.room(1);
        InteractionWiredChest chest = mock(InteractionWiredChest.class);
        ChestStorage contents = mock(ChestStorage.class);
        when(chest.answersWired()).thenReturn(true);
        when(chest.getContents()).thenReturn(contents);
        when(contents.total(ChestStorage.KIND_CURRENCY)).thenReturn(2);
        when(contents.total(ChestStorage.KIND_FURNI)).thenReturn(3);
        when(contents.count(ChestStorage.KIND_FURNI, TYPE_BASE)).thenReturn(2);
        when(room.getHabboItem(CHEST)).thenReturn(chest);

        HabboItem typeFurni = mock(HabboItem.class);
        Item typeBase = mock(Item.class);
        when(typeBase.getId()).thenReturn(TYPE_BASE);
        when(typeFurni.getBaseItem()).thenReturn(typeBase);
        when(room.getHabboItem(TYPE_FURNI)).thenReturn(typeFurni);

        RoomVariableManager globals = mock(RoomVariableManager.class);
        when(globals.getDefinitionInfo(VARIABLE)).thenReturn(mock(WiredVariableDefinitionInfo.class));
        when(globals.getCurrentValue(VARIABLE)).thenReturn(5);
        when(room.getRoomVariableManager()).thenReturn(globals);
        return room;
    }

    private static boolean save(InteractionWiredCondition box, Room room, WiredSettings settings) {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(5);
        RoomManager rooms = mock(RoomManager.class);
        when(rooms.getRoom(anyInt())).thenReturn(room);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::configuration).thenReturn(config);
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
            return box.saveData(settings);
        }
    }

    private static WiredSettings settings(int[] ints, String[] variableIds, int[] picks, int[] picks2) {
        WiredSettings settings = new WiredSettings(ints, "", picks, 0);
        settings.setVariableIds(variableIds);
        settings.setFurniIds2(picks2);
        return settings;
    }

    private static void load(InteractionWiredCondition box, Room room, String json) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(json);
        box.loadWiredData(set, room);
    }

    @Test
    void chestHasItemsReadsAVariableOnAnyOfficialTarget() {
        Room room = room();
        WiredConditionChestHasItems box =
                new WiredConditionChestHasItems(10, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        // 5 contents compared to the global variable (5): "=" passes, ">" does not.
        assertTrue(save(
                box,
                room,
                settings(
                        new int[] {0, 1, WiredValueVariable.TARGET_GLOBAL, WiredComparison.EQUAL},
                        new String[] {String.valueOf(VARIABLE)},
                        new int[] {CHEST},
                        new int[0])));
        WiredLegacyWireReader wire = WiredLegacyWireReader.read(box, room);
        assertArrayEquals(new int[] {0, 1, -10, WiredComparison.EQUAL}, wire.intParams);
        assertEquals("", wire.stringParam);
        assertArrayEquals(new String[] {"room:" + VARIABLE}, box.officialVariableIds());
        assertTrue(box.evaluate(WiredConditionTestSupport.context(room, null)));

        assertTrue(save(
                box,
                room,
                settings(
                        new int[] {0, 1, WiredValueVariable.TARGET_CONTEXT, WiredComparison.GREATER},
                        new String[] {String.valueOf(VARIABLE)},
                        new int[] {CHEST},
                        new int[0])));
        assertArrayEquals(
                new int[] {0, 1, -20, WiredComparison.GREATER}, WiredLegacyWireReader.read(box, room).intParams);
        // No context variable in this run: the condition fails rather than guess.
        assertFalse(box.evaluate(WiredConditionTestSupport.context(room, null)));
    }

    @Test
    void chestHasItemsLoadsTheUserAndRoomChoiceOfOlderBoxes() throws Exception {
        Room room = room();
        WiredConditionChestHasItems box =
                new WiredConditionChestHasItems(11, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        load(
                box,
                room,
                "{\"amount\":4,\"comparison\":5,\"chestIds\":[1],\"amountMode\":1,\"amountTarget\":0,"
                        + "\"amountVariableToken\":\"custom:7\"}");
        assertArrayEquals(new int[] {4, 1, 1, 5}, WiredLegacyWireReader.read(box, room).intParams);
        assertArrayEquals(new String[] {"user:7"}, box.officialVariableIds());

        // The oldest boxes have a typed amount only; their missing target reads as before (the user's).
        load(box, room, "{\"amount\":4,\"comparison\":5,\"chestIds\":[1]}");
        assertArrayEquals(new int[] {4, 0, 1, 5}, WiredLegacyWireReader.read(box, room).intParams);
        assertTrue(box.evaluate(WiredConditionTestSupport.context(room, null)));

        // An older client sent the token in the string.
        assertTrue(save(
                box,
                room,
                new WiredSettings(new int[] {0, 1, 1, WiredComparison.EQUAL}, "custom:7", new int[] {CHEST}, 0)));
        assertArrayEquals(new String[] {"user:7"}, box.officialVariableIds());
    }

    @Test
    void chestHasItemsReadsItsChestsFromTheOfficialSourceSlot() throws Exception {
        Room room = room();
        HabboItem chest = room.getHabboItem(CHEST);
        WiredConditionChestHasItems box =
                new WiredConditionChestHasItems(14, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);

        // Picks (the default): the picked chest holds 5.
        assertTrue(save(
                box,
                room,
                settings(new int[] {5, 0, -10, WiredComparison.EQUAL}, new String[0], new int[] {CHEST}, new int[0])));
        assertArrayEquals(new int[] {100}, box.officialFurniSources());
        assertTrue(box.evaluate(WiredConditionTestSupport.context(room, null)));

        // The trigger: the chest the event happened on, with nothing picked.
        assertTrue(save(
                box,
                room,
                withSources(
                        settings(new int[] {5, 0, -10, WiredComparison.EQUAL}, new String[0], new int[0], new int[0]),
                        0)));
        assertArrayEquals(new int[] {0}, box.officialFurniSources());
        WiredContext onChest = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                        .sourceItem(chest)
                        .build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
        assertTrue(box.evaluate(onChest));

        // Compared above its 5, the trigger's chest fails: it is what is counted.
        assertTrue(save(
                box,
                room,
                withSources(
                        settings(
                                new int[] {6, 0, -10, WiredComparison.GREATER_EQUAL},
                                new String[0],
                                new int[0],
                                new int[0]),
                        0)));
        assertFalse(box.evaluate(onChest));

        // The source survives a reload; a box saved before the slot keeps reading its picks.
        load(box, room, box.getWiredData());
        assertArrayEquals(new int[] {0}, box.officialFurniSources());
        load(box, room, "{\"amount\":5,\"comparison\":1,\"chestIds\":[1],\"valueTarget\":-10}");
        assertArrayEquals(new int[] {100}, box.officialFurniSources());
        assertTrue(box.evaluate(WiredConditionTestSupport.context(room, null)));
    }

    private static WiredSettings withSources(WiredSettings settings, int... furniSources) {
        settings.setFurniSourceTypes(furniSources);
        return settings;
    }

    @Test
    void chestHasItemTypesCountsThePickedTypesInTheSecondPicks() {
        Room room = room();
        WiredConditionChestHasItemType box =
                new WiredConditionChestHasItemType(12, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        assertTrue(save(
                box,
                room,
                settings(
                        new int[] {2, 0, WiredValueVariable.TARGET_GLOBAL, WiredComparison.EQUAL},
                        new String[0],
                        new int[] {TYPE_FURNI},
                        new int[] {CHEST})));
        WiredLegacyWireReader wire = WiredLegacyWireReader.read(box, room);
        assertArrayEquals(new int[] {TYPE_FURNI}, wire.furniIds);
        assertArrayEquals(new int[] {2, 0, -10, WiredComparison.EQUAL}, wire.intParams);
        assertArrayEquals(new int[] {CHEST}, box.officialFurniIds2());
        assertArrayEquals(new int[] {100, 101}, box.officialFurniSources());
        assertTrue(box.evaluate(WiredConditionTestSupport.context(room, null)));

        // The same picks against the global variable (5): 2 < 5.
        assertTrue(save(
                box,
                room,
                settings(
                        new int[] {0, 1, WiredValueVariable.TARGET_GLOBAL, WiredComparison.LESS},
                        new String[] {String.valueOf(VARIABLE)},
                        new int[] {TYPE_FURNI},
                        new int[] {CHEST})));
        assertTrue(box.evaluate(WiredConditionTestSupport.context(room, null)));

        // A picked chest that is not a chest is refused.
        when(room.getHabboItem(3)).thenReturn(mock(HabboItem.class));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.eu.habbo.habbohotel.items.interactions.wired.WiredSaveRefusedException.class,
                () -> save(
                        box,
                        room,
                        settings(new int[] {1, 0, -10, 5}, new String[0], new int[] {TYPE_FURNI}, new int[] {3})));
    }

    @Test
    void chestHasItemTypesKeepsTheTypedTypeOfAnOlderBox() throws Exception {
        Room room = room();
        WiredConditionChestHasItemType box =
                new WiredConditionChestHasItemType(13, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        // [baseItemId, amount, comparison] and the chests as picks, from before the official layout.
        load(box, room, "{\"baseItemId\":900,\"amount\":2,\"comparison\":5,\"chestIds\":[1]}");
        WiredLegacyWireReader wire = WiredLegacyWireReader.read(box, room);
        assertArrayEquals(new int[0], wire.furniIds);
        assertArrayEquals(new int[] {2, 0, -10, 5}, wire.intParams);
        assertArrayEquals(new int[] {CHEST}, box.officialFurniIds2());
        assertTrue(box.evaluate(WiredConditionTestSupport.context(room, null)));

        // Saved again without picked types, the typed type stays; with picked types, they take over.
        assertTrue(save(box, room, settings(new int[] {3, 0, -10, 5}, new String[0], new int[0], new int[] {CHEST})));
        assertEquals(TYPE_BASE, box.getLegacyBaseItemId());
        assertFalse(box.evaluate(WiredConditionTestSupport.context(room, null)));
        assertTrue(save(
                box, room, settings(new int[] {2, 0, -10, 5}, new String[0], new int[] {TYPE_FURNI}, new int[] {CHEST
                })));
        assertEquals(0, box.getLegacyBaseItemId());
        assertTrue(box.evaluate(WiredConditionTestSupport.context(room, null)));
    }
}
