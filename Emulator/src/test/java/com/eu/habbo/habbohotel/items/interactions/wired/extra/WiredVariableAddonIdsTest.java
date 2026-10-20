package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.protocol.WiredEditorState;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/** The variable add-ons read their variables from the official variable ids and send them back there. */
class WiredVariableAddonIdsTest {
    private final Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);

    private static ResultSet row(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    private static WiredSettings settings(int[] ints, String text, String... variableIds) {
        WiredSettings settings = new WiredSettings(ints, text, new int[0], -1);
        settings.setVariableIds(variableIds);
        return settings;
    }

    @Test
    void theSelectorFilterTakesItsAmountVariableFromTheVariableIds() throws Exception {
        WiredExtraFilterFurni box = new WiredExtraFilterFurni(1, 1, mock(Item.class), "", 0, 0);
        assertTrue(box.saveData(settings(new int[] {1, 1, -10, 1}, "", "room:8"), null));
        WiredEditorState state = WiredEditorState.of(box, this.room);
        assertEquals("", state.stringParam());
        assertArrayEquals(new String[] {"room:8"}, state.variableIds());

        // An older client sent the id as the string.
        WiredExtraFilterUser legacy = new WiredExtraFilterUser(2, 1, mock(Item.class), "", 0, 0);
        assertTrue(legacy.saveData(settings(new int[] {1, 1, 1, 1}, "5"), null));
        assertArrayEquals(new String[] {"user:5"}, legacy.officialVariableIds());

        WiredExtraFilterUser reopened = new WiredExtraFilterUser(3, 1, mock(Item.class), "", 0, 0);
        reopened.loadWiredData(row(legacy.getWiredData()), null);
        assertArrayEquals(new String[] {"user:5"}, reopened.officialVariableIds());
    }

    @Test
    void theJumpStrengthSendsItsVariableAsTheVariableId() throws Exception {
        WiredExtraMovementCurve box = new WiredExtraMovementCurve(4, 1, mock(Item.class), "", 0, 0);
        assertTrue(box.saveData(settings(new int[] {1, 80, -10, 7, 100}, "", "room:9"), null));
        WiredEditorState state = WiredEditorState.of(box, this.room);
        assertEquals("", state.stringParam());
        assertArrayEquals(new String[] {"room:9"}, state.variableIds());

        WiredExtraMovementCurve legacy = new WiredExtraMovementCurve(5, 1, mock(Item.class), "", 0, 0);
        assertTrue(legacy.saveData(settings(new int[] {1, 80, 0, 7, 100}, "custom:9"), null));
        assertArrayEquals(new String[] {"furni:9"}, legacy.officialVariableIds());
        WiredExtraMovementCurve reopened = new WiredExtraMovementCurve(6, 1, mock(Item.class), "", 0, 0);
        reopened.loadWiredData(row(legacy.getWiredData()), null);
        assertArrayEquals(new String[] {"furni:9"}, reopened.officialVariableIds());
    }

    @Test
    void aStoredVariableFilterGoesOutWithBothVariablesAsVariableIds() throws Exception {
        WiredExtraFilterFurniByVariable box = new WiredExtraFilterFurniByVariable(9, 1, mock(Item.class), "", 0, 0);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));
            box.loadWiredData(
                    row("{\"sortBy\":0,\"amountMode\":1,\"amountConstantValue\":1,\"referenceTargetType\":3,"
                            + "\"referenceUserSource\":0,\"referenceFurniSource\":0,"
                            + "\"variableToken\":\"internal:@altitude\",\"variableItemId\":0,"
                            + "\"referenceVariableToken\":\"custom:8\",\"referenceVariableItemId\":8}"),
                    this.room);
            WiredEditorState state = WiredEditorState.of(box, this.room);
            assertEquals("", state.stringParam());
            assertArrayEquals(new String[] {"-123", "room:8"}, state.variableIds());
        }
    }

    @Test
    void storedPlaceholderAndCapturerRowsGoOutInAirsLayout() throws Exception {
        WiredExtraTextOutputVariable placeholder = new WiredExtraTextOutputVariable(7, 1, mock(Item.class), "", 0, 0);
        placeholder.loadWiredData(
                row("{\"targetType\":0,\"variableToken\":\"internal:@level\",\"displayType\":1,\"placeholderType\":2,"
                        + "\"placeholderName\":\"lvl\",\"delimiter\":\" / \",\"userSource\":0,\"furniSource\":0}"),
                null);
        WiredEditorState state = WiredEditorState.of(placeholder, this.room);
        // AIR: the name, then the delimiter for every holder.
        assertEquals("lvl\t / ", state.stringParam());
        assertArrayEquals(new String[] {"-210"}, state.variableIds());

        WiredExtraTextInputVariable capturer = new WiredExtraTextInputVariable(8, 1, mock(Item.class), "", 0, 0);
        capturer.loadWiredData(
                row("{\"variableToken\":\"custom:33\",\"capturerName\":\"answer\",\"displayType\":1}"), null);
        WiredEditorState capturerState = WiredEditorState.of(capturer, this.room);
        assertEquals("answer", capturerState.stringParam());
        assertArrayEquals(new String[] {"ctx:33"}, capturerState.variableIds());
    }
}
