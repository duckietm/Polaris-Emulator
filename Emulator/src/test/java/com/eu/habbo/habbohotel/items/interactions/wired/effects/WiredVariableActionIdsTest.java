package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.protocol.WiredEditorState;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Give, remove and change variable read their variables from the official variable ids and send them back there. */
class WiredVariableActionIdsTest {
    private final Room room = mock(Room.class);
    private final GameEnvironment environment = mock(GameEnvironment.class);

    WiredVariableActionIdsTest() {
        RoomManager rooms = mock(RoomManager.class);
        when(this.environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(anyInt())).thenReturn(this.room);
        WiredVariableDefinitionInfo info = mock(WiredVariableDefinitionInfo.class);
        when(info.hasValue()).thenReturn(true);
        RoomUserVariableManager users = mock(RoomUserVariableManager.class);
        RoomFurniVariableManager furni = mock(RoomFurniVariableManager.class);
        RoomVariableManager global = mock(RoomVariableManager.class);
        when(users.getDefinitionInfo(anyInt())).thenReturn(info);
        when(users.hasDefinition(anyInt())).thenReturn(true);
        when(furni.getDefinitionInfo(anyInt())).thenReturn(info);
        when(global.getDefinitionInfo(anyInt())).thenReturn(info);
        when(this.room.getUserVariableManager()).thenReturn(users);
        when(this.room.getFurniVariableManager()).thenReturn(furni);
        when(this.room.getRoomVariableManager()).thenReturn(global);
    }

    private static ResultSet stored(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    private static WiredSettings settings(int[] ints, String text, String... variableIds) {
        WiredSettings settings = new WiredSettings(ints, text, new int[0], 0);
        settings.setVariableIds(variableIds);
        return settings;
    }

    private void saveWithEmulator(ThrowingSave save) throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(120);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(this.environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            platform.when(WiredPlatform::gameEnvironment).thenReturn(this.environment);
            save.run();
        }
    }

    private interface ThrowingSave {
        void run() throws Exception;
    }

    @Test
    void giveVariableSavesFromTheVariableIdsAndSendsNoString() throws Exception {
        WiredEffectGiveVariable box = new WiredEffectGiveVariable(10, 1, mock(Item.class), "", 0, 0);
        this.saveWithEmulator(() ->
                assertTrue(box.saveData(settings(new int[] {1, 0, 0, 0, 1, -10, 0}, "", "user:40", "room:41"), null)));

        assertEquals(40, box.getVariableItemId());
        WiredEditorState state = WiredEditorState.of(box, this.room);
        assertEquals("", state.stringParam());
        assertArrayEquals(new String[] {"user:40", "room:41"}, state.variableIds());
    }

    @Test
    void giveVariableStillReadsAnOlderClientsStringAndStoredRows() throws Exception {
        WiredEffectGiveVariable legacy = new WiredEffectGiveVariable(11, 1, mock(Item.class), "", 0, 0);
        this.saveWithEmulator(() ->
                assertTrue(legacy.saveData(settings(new int[] {1, 0, 0, 0, 1, -10, 0}, "40\t41"), null)));
        assertArrayEquals(new String[] {"user:40", "room:41"}, legacy.officialVariableIds());

        WiredEffectGiveVariable reopened = new WiredEffectGiveVariable(12, 1, mock(Item.class), "", 0, 0);
        reopened.loadWiredData(stored(legacy.getWiredData()), this.room);
        assertArrayEquals(new String[] {"user:40", "room:41"}, reopened.officialVariableIds());

        WiredEffectGiveVariable rights = new WiredEffectGiveVariable(13, 1, mock(Item.class), "", 0, 0);
        rights.loadWiredData(
                stored("{\"variableItemId\":0,\"targetType\":0,\"internalVariableKey\":\"@has_rights\"}"), null);
        // Habbo's id of @has_rights.
        assertArrayEquals(new String[] {"-221"}, rights.officialVariableIds());
    }

    @Test
    void removeVariableSavesFromTheVariableIdsAndSendsNoString() throws Exception {
        WiredEffectRemoveVariable box = new WiredEffectRemoveVariable(20, 1, mock(Item.class), "", 0, 0);
        this.saveWithEmulator(() -> assertTrue(box.saveData(settings(new int[] {0}, "", "furni:40"), null)));

        WiredEditorState state = WiredEditorState.of(box, this.room);
        assertEquals("", state.stringParam());
        assertArrayEquals(new int[] {0}, state.intParams());
        assertArrayEquals(new String[] {"furni:40"}, state.variableIds());

        WiredEffectRemoveVariable legacy = new WiredEffectRemoveVariable(21, 1, mock(Item.class), "", 0, 0);
        this.saveWithEmulator(() -> assertTrue(legacy.saveData(settings(new int[] {1}, "internal:@has_rights"), null)));
        assertArrayEquals(new String[] {"-221"}, legacy.officialVariableIds());
    }

    @Test
    void changeVariableUsesAirsSevenIntsVariableIdsAndSourceSlots() throws Exception {
        WiredEffectChangeVariableValue box = new WiredEffectChangeVariableValue(30, 1, mock(Item.class), "", 0, 0);
        WiredSettings settings = settings(new int[] {0, 1, 1, 0, 0, -10, 0}, "", "-120", "room:41");
        settings.setFurniSourceTypes(new int[] {200, 0});
        settings.setUserSourceTypes(new int[] {0, 0});
        this.saveWithEmulator(() -> assertTrue(box.saveData(settings, null)));

        WiredEditorState state = WiredEditorState.of(box, this.room);
        assertArrayEquals(new int[] {0, 1, 1, 0, 0, -10, 0}, state.intParams());
        assertEquals("", state.stringParam());
        assertArrayEquals(new String[] {"-120", "room:41"}, state.variableIds());
        assertArrayEquals(new int[] {200, 0}, state.furniSources());

        // A negative constant is a long in two ints.
        WiredEffectChangeVariableValue constant = new WiredEffectChangeVariableValue(31, 1, mock(Item.class), "", 0, 0);
        this.saveWithEmulator(() ->
                assertTrue(constant.saveData(settings(new int[] {1, 2, 0, -1, -5, 1, 0}, "", "user:40"), null)));
        WiredEditorState constantState = WiredEditorState.of(constant, this.room);
        assertArrayEquals(new int[] {1, 2, 0, -1, -5, 1, 0}, constantState.intParams());
        assertArrayEquals(new String[] {"user:40"}, constantState.variableIds());
    }

    @Test
    void changeVariableStillReadsOurTenIntsAndStoredRows() throws Exception {
        WiredEffectChangeVariableValue legacy = new WiredEffectChangeVariableValue(32, 1, mock(Item.class), "", 0, 0);
        // Ours: [user target, add, variable, 0, room target, sources..., same holder], tokens in the string.
        this.saveWithEmulator(() -> assertTrue(
                legacy.saveData(settings(new int[] {0, 1, 1, 0, 3, 0, 0, 0, 0, 0}, "custom:40\tcustom:41\t\t"), null)));
        WiredEditorState state = WiredEditorState.of(legacy, this.room);
        assertArrayEquals(new int[] {1, 1, 1, 0, 0, -10, 0}, state.intParams());
        assertArrayEquals(new String[] {"user:40", "room:41"}, state.variableIds());

        WiredEffectChangeVariableValue reopened = new WiredEffectChangeVariableValue(33, 1, mock(Item.class), "", 0, 0);
        reopened.loadWiredData(stored(legacy.getWiredData()), this.room);
        assertArrayEquals(new String[] {"user:40", "room:41"}, reopened.officialVariableIds());
        assertEquals(legacy.getWiredData(), reopened.getWiredData());
    }
}
