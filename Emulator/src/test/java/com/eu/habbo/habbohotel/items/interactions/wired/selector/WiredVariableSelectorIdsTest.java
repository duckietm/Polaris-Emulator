package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredEditorState;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/** The with-variable selectors read and send their variables as official variable ids. */
class WiredVariableSelectorIdsTest {
    private final Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);

    private interface Body {
        void run() throws Exception;
    }

    private void withRoom(Body body) throws Exception {
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(anyInt())).thenReturn(this.room);
        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
            body.run();
        }
    }

    private static WiredSettings settings(int[] ints, String text, String... variableIds) {
        WiredSettings settings = new WiredSettings(ints, text, new int[0], -1);
        settings.setVariableIds(variableIds);
        return settings;
    }

    @Test
    void usersWithVariableSavesFromTheVariableIdsAndSendsNoString() throws Exception {
        WiredEffectUsersWithVariable box = new WiredEffectUsersWithVariable(1, 1, mock(Item.class), "", 0, 0);
        this.withRoom(() -> {
            assertTrue(box.saveData(settings(new int[] {2, 2, 0, 0, 1, 0}, "", "-210", "-211"), null));
            WiredEditorState state = WiredEditorState.of(box, this.room);
            assertEquals("", state.stringParam());
            assertArrayEquals(new String[] {"-210", "-211"}, state.variableIds());
        });
    }

    @Test
    void anOlderClientsStringAndAStoredRowStillWork() throws Exception {
        WiredEffectFurniWithVariable legacy = new WiredEffectFurniWithVariable(2, 1, mock(Item.class), "", 0, 0);
        WiredEffectFurniWithVariable reopened = new WiredEffectFurniWithVariable(3, 1, mock(Item.class), "", 0, 0);
        this.withRoom(() -> {
            assertTrue(legacy.saveData(settings(new int[] {2, 1, 0, 4, 0, 0}, "internal:@altitude\t"), null));
            // A constant reference: only the variable.
            assertArrayEquals(new String[] {"-123"}, legacy.officialVariableIds());

            String wiredData = legacy.getWiredData();
            ResultSet stored = mock(ResultSet.class);
            when(stored.getString("wired_data")).thenReturn(wiredData);
            reopened.loadWiredData(stored, this.room);
            assertArrayEquals(new String[] {"-123"}, reopened.officialVariableIds());
        });
    }
}
