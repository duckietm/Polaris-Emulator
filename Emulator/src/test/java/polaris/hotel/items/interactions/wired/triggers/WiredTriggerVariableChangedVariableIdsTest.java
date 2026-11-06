package polaris.hotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.rooms.WiredVariableDefinitionInfo;
import polaris.hotel.rooms.variables.RoomFurniVariableManager;
import polaris.hotel.rooms.variables.RoomUserVariableManager;
import polaris.hotel.wired.WiredVariableChangeOrigin;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.protocol.WiredEditorState;

/** Variable Changed takes its variable from the official variable ids and sends it back there, as AIR. */
class WiredTriggerVariableChangedVariableIdsTest {

    private static Room room() {
        Room room = mock(Room.class);
        RoomUserVariableManager users = mock(RoomUserVariableManager.class);
        RoomFurniVariableManager furni = mock(RoomFurniVariableManager.class);
        when(room.getUserVariableManager()).thenReturn(users);
        when(room.getFurniVariableManager()).thenReturn(furni);
        when(users.getDefinitionInfo(88)).thenReturn(mock(WiredVariableDefinitionInfo.class));
        when(furni.getDefinitionInfo(89)).thenReturn(mock(WiredVariableDefinitionInfo.class));
        return room;
    }

    private static WiredTriggerVariableChanged box() {
        return new WiredTriggerVariableChanged(70, 1, mock(Item.class), "", 0, 0);
    }

    private static WiredTriggerVariableChanged save(Room room, WiredSettings settings) {
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(anyInt())).thenReturn(room);
        WiredTriggerVariableChanged trigger = box();
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertTrue(trigger.saveData(settings));
        }
        return trigger;
    }

    private static WiredEvent changed(Room room, int definitionItemId, int targetType) {
        return WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .variableTargetType(targetType)
                .variableDefinitionItemId(definitionItemId)
                .variableChangeKind(WiredEvent.VariableChangeKind.INCREASED)
                .variableChangeOrigin(WiredVariableChangeOrigin.WIRED)
                .build();
    }

    @Test
    void savesTheVariableFromTheVariableIdsAndSendsItBackThere() {
        Room room = room();
        WiredSettings settings = new WiredSettings(new int[] {1, 1, 1, 7, -1, 0, 1}, "", new int[0], 0);
        settings.setVariableIds(new String[] {"user:88"});
        WiredTriggerVariableChanged trigger = save(room, settings);

        assertTrue(trigger.matches(trigger, changed(room, 88, 0)));
        WiredEditorState state = WiredEditorState.of(trigger, room);
        assertEquals("", state.stringParam());
        assertArrayEquals(new String[] {"user:88"}, state.variableIds());
    }

    @Test
    void airsSixIntsTakeTheTargetFromTheVariableId() {
        Room room = room();
        WiredSettings settings = new WiredSettings(new int[] {1, 1, 1, 7, -1, 0}, "", new int[0], 0);
        settings.setVariableIds(new String[] {"furni:89"});
        WiredTriggerVariableChanged trigger = save(room, settings);

        assertTrue(trigger.matches(trigger, changed(room, 89, 1)));
        assertFalse(trigger.matches(trigger, changed(room, 89, 0)));
        assertArrayEquals(new String[] {"furni:89"}, trigger.officialVariableIds());
    }

    @Test
    void anOlderClientsTokenInTheStringStillSaves() {
        Room room = room();
        WiredSettings settings = new WiredSettings(new int[] {1, 1, 1, 7, -1, 0, 1}, "custom:88", new int[0], 0);
        WiredTriggerVariableChanged trigger = save(room, settings);

        assertTrue(trigger.matches(trigger, changed(room, 88, 0)));
        assertArrayEquals(new String[] {"user:88"}, trigger.officialVariableIds());
    }

    @Test
    void aStoredBoxLoadsUnchangedAndGoesOutWithItsVariableId() throws Exception {
        Room room = room();
        ResultSet stored = mock(ResultSet.class);
        when(stored.getString("wired_data"))
                .thenReturn(
                        "{\"variableToken\":\"internal:@projectile.animation.tiles_travelled\",\"variableItemId\":0,"
                                + "\"targetType\":1,\"createdEnabled\":false,\"valueChangedEnabled\":true,"
                                + "\"increasedEnabled\":true,\"decreasedEnabled\":true,\"unchangedEnabled\":true,"
                                + "\"deletedEnabled\":false,\"originMask\":-1}");
        WiredTriggerVariableChanged trigger = box();
        trigger.loadWiredData(stored, room);

        assertArrayEquals(new String[] {"-2100"}, trigger.officialVariableIds());
        assertEquals("", WiredEditorState.of(trigger, room).stringParam());
        assertTrue(trigger.getWiredData()
                .contains("\"variableToken\":\"internal:@projectile.animation.tiles_travelled\""));
    }
}
