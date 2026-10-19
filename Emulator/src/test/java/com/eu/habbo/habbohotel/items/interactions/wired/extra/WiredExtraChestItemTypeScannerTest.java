package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The chest item type scanner counts the furni of the picked types in the picked chests, all of them or only what
 * the chests show, and stores the count in its context variable for the run.
 */
class WiredExtraChestItemTypeScannerTest {
    private static final int CHAIR = 1389;
    private static final int LAMP = 77;
    private static final int VARIABLE = 40;
    private static final int CHAIR_PICK = 300;
    private static final int LAMP_PICK = 301;
    private static final int CHEST = 500;

    @Test
    void countsEveryItemOfThePickedTypes() {
        InteractionWiredChestFurni chest = chest(true, CHAIR, CHAIR, LAMP, 9);

        assertEquals(3, WiredExtraChestItemTypeScanner.count(List.of(chest), Set.of(CHAIR, LAMP), 0));
        assertEquals(2, WiredExtraChestItemTypeScanner.count(List.of(chest), Set.of(CHAIR), 0));
    }

    @Test
    void displayedModeCountsOnlyWhatTheLidShows() {
        InteractionWiredChestFurni chest = chest(true, CHAIR, CHAIR, LAMP);

        // The preview is off: nothing is on show.
        assertEquals(0, WiredExtraChestItemTypeScanner.count(List.of(chest), Set.of(CHAIR, LAMP), 1));

        // One item of each type, up to the preview amount.
        chest.getContents().setPreview(1, 4);
        assertEquals(2, WiredExtraChestItemTypeScanner.count(List.of(chest), Set.of(CHAIR, LAMP), 1));
        chest.getContents().setPreview(1, 1);
        assertEquals(1, WiredExtraChestItemTypeScanner.count(List.of(chest), Set.of(CHAIR, LAMP), 1));
        assertEquals(0, WiredExtraChestItemTypeScanner.count(List.of(chest), Set.of(LAMP), 1));
    }

    @Test
    void aChestThatDoesNotAnswerWiredIsNotRead() {
        InteractionWiredChestFurni silent = chest(false, CHAIR, CHAIR);
        InteractionWiredChestFurni open = chest(true, CHAIR);

        assertEquals(0, WiredExtraChestItemTypeScanner.count(List.of(silent), Set.of(CHAIR), 0));
        assertEquals(1, WiredExtraChestItemTypeScanner.count(List.of(silent, open), Set.of(CHAIR), 0));
    }

    @Test
    void noTypesOrNoChestsCountZero() {
        InteractionWiredChestFurni chest = chest(true, CHAIR);

        assertEquals(0, WiredExtraChestItemTypeScanner.count(List.of(chest), Set.of(), 0));
        assertEquals(0, WiredExtraChestItemTypeScanner.count(List.of(), Set.of(CHAIR), 0));
    }

    @Test
    void theRunGetsTheCountInItsContextVariable() throws Exception {
        InteractionWiredChestFurni chest = chest(true, CHAIR, CHAIR, LAMP);
        Room room = room(contextVariable(VARIABLE));
        HabboItem chair = furni(CHAIR);
        when(room.getHabboItem(CHAIR_PICK)).thenReturn(chair);
        when(room.getHabboItem(CHEST)).thenReturn(chest);

        WiredExtraChestItemTypeScanner scanner = load("{\"mode\":0,\"variableItemId\":" + VARIABLE
                + ",\"furniSource\":100,\"furniSource2\":101,\"typeItemIds\":[" + CHAIR_PICK + "],\"chestIds\":["
                + CHEST + "]}");
        WiredContext ctx = context(room);

        assertTrue(scanner.scan(ctx));
        assertEquals(2, ctx.contextVariables().getValue(VARIABLE));
    }

    @Test
    void emptyPicksStoreZero() throws Exception {
        Room room = room(contextVariable(VARIABLE));
        WiredExtraChestItemTypeScanner scanner = load("{\"mode\":1,\"variableItemId\":" + VARIABLE + "}");
        WiredContext ctx = context(room);

        assertTrue(scanner.scan(ctx));
        assertEquals(0, ctx.contextVariables().getValue(VARIABLE));
    }

    @Test
    void withoutAVariableNothingIsWritten() throws Exception {
        Room room = room(contextVariable(VARIABLE));
        WiredContext ctx = context(room);

        assertFalse(load("").scan(ctx));
        assertNull(ctx.contextVariables().getValue(VARIABLE));
    }

    @Test
    void loadsItsOwnRow() throws Exception {
        WiredExtraChestItemTypeScanner scanner = load("{\"mode\":1,\"variableItemId\":" + VARIABLE
                + ",\"furniSource\":200,\"furniSource2\":101,\"typeItemIds\":[" + CHAIR_PICK + "," + LAMP_PICK
                + "],\"chestIds\":[" + CHEST + "]}");

        assertEquals(1, scanner.getMode());
        assertEquals(VARIABLE, scanner.getVariableItemId());
        assertArrayEquals(new int[] {200, 101}, scanner.officialFurniSources());
        assertArrayEquals(new String[] {String.valueOf(VARIABLE)}, scanner.officialVariableIds());
        assertEquals(List.of(CHAIR_PICK, LAMP_PICK), scanner.getTypeItemIds());
        assertArrayEquals(new int[] {CHEST}, scanner.officialFurniIds2());
    }

    @Test
    void aRowOfTheOldSelectorKeepsItsChestsAndOpensOnTheDefaults() throws Exception {
        WiredExtraChestItemTypeScanner scanner =
                load("{\"filterExisting\":true,\"invert\":true,\"chestIds\":[" + CHEST + "],\"delay\":3}");

        assertEquals(0, scanner.getMode());
        assertEquals(0, scanner.getVariableItemId());
        assertArrayEquals(new int[] {100, 101}, scanner.officialFurniSources());
        assertEquals(List.of(), scanner.getTypeItemIds());
        assertEquals(List.of(CHEST), scanner.getChestIds());
    }

    @Test
    void unreadableRowsOpenOnTheDefaults() throws Exception {
        for (String row : new String[] {null, "", "not json", "{\"mode\":\"x\"", "{\"chestIds\":\"oops\"}"}) {
            WiredExtraChestItemTypeScanner scanner = load(row);

            assertEquals(0, scanner.getMode());
            assertEquals(0, scanner.getVariableItemId());
            assertEquals(List.of(), scanner.getChestIds());
        }
    }

    @Test
    void savesTheOfficialLayout() throws Exception {
        Room room = room(contextVariable(VARIABLE));
        InteractionWiredChestFurni chest = chest(true, CHAIR);
        when(room.getHabboItem(CHEST)).thenReturn(chest);
        WiredExtraChestItemTypeScanner scanner = load("");

        WiredSettings settings = settings(room, 1, new int[] {CHAIR_PICK}, new int[] {CHEST}, "" + VARIABLE);
        settings.setFurniSourceTypes(new int[] {100, 101});
        assertTrue(scanner.saveData(settings, null));

        assertEquals(1, scanner.getMode());
        assertEquals(VARIABLE, scanner.getVariableItemId());
        assertEquals(List.of(CHAIR_PICK), scanner.getTypeItemIds());
        assertEquals(List.of(CHEST), scanner.getChestIds());

        WiredExtraChestItemTypeScanner reloaded = load(scanner.getWiredData());
        assertEquals(1, reloaded.getMode());
        assertEquals(List.of(CHEST), reloaded.getChestIds());
    }

    @Test
    void refusesAMissingVariableAndPicksThatAreNotFurniChests() throws Exception {
        Room room = room(contextVariable(VARIABLE));
        HabboItem chair = furni(CHAIR);
        when(room.getHabboItem(CHAIR_PICK)).thenReturn(chair);
        WiredExtraChestItemTypeScanner scanner = load("");

        assertThrows(
                WiredSaveException.class, () -> scanner.saveData(settings(room, 0, new int[0], new int[0], ""), null));
        assertThrows(
                WiredSaveException.class,
                () -> scanner.saveData(settings(room, 0, new int[0], new int[0], "999"), null));
        assertThrows(
                WiredSaveException.class,
                () -> scanner.saveData(settings(room, 0, new int[0], new int[] {CHAIR_PICK}, "" + VARIABLE), null));
    }

    private static InteractionWiredChestFurni chest(boolean answersWired, int... baseItemIds) {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(CHEST, 2, mock(Item.class), "", 0, 0);
        if (answersWired) chest.getContents().enableWired();
        for (int baseItemId : baseItemIds) {
            ChestFurniStoredItem stored = new ChestFurniStoredItem();
            stored.baseItemId = baseItemId;
            stored.spriteId = baseItemId + 10_000;
            chest.getContents().addFurniItem(stored);
        }
        return chest;
    }

    private static HabboItem furni(int baseItemId) {
        HabboItem item = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(base.getId()).thenReturn(baseItemId);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }

    private static WiredExtraContextVariable contextVariable(int id) throws Exception {
        WiredExtraContextVariable variable = new WiredExtraContextVariable(id, 1, mock(Item.class), "", 0, 0);
        variable.loadWiredData(data("{\"variableName\":\"in_chest\",\"hasValue\":true}"), null);
        return variable;
    }

    private static Room room(InteractionWiredExtra... boxes) {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(new LinkedHashSet<>(List.of(boxes)));
        when(special.getExtras(anyInt(), anyInt())).thenReturn(new LinkedHashSet<>());
        for (InteractionWiredExtra box : boxes)
            when(special.getExtra(box.getId())).thenReturn(box);
        return room;
    }

    private static WiredSettings settings(Room room, int mode, int[] types, int[] chests, String variable) {
        WiredSettings settings = new WiredSettings(new int[] {mode}, "", types, -1);
        settings.setRoom(room);
        settings.setFurniIds2(chests);
        settings.setVariableIds(new String[] {variable});
        return settings;
    }

    private static WiredExtraChestItemTypeScanner load(String json) throws Exception {
        WiredExtraChestItemTypeScanner scanner = new WiredExtraChestItemTypeScanner(7, 1, mock(Item.class), "", 0, 0);
        scanner.loadWiredData(data(json), null);
        return scanner;
    }

    private static ResultSet data(String json) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(json);
        return set;
    }

    private static WiredContext context(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
    }
}
