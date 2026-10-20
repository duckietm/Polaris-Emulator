package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.protocol.WiredEditorState;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/** Has variable, variable value match and variable age match read and send their variables as official variable ids. */
class WiredVariableConditionIdsTest {
    private final Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);

    private interface Body {
        void run() throws Exception;
    }

    private static void withEmulator(Body body) throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(20);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment)
                    .thenReturn(mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS));
            body.run();
        }
    }

    private static WiredSettings settings(int[] ints, String text, String... variableIds) {
        WiredSettings settings = new WiredSettings(ints, text, new int[0], -1);
        settings.setVariableIds(variableIds);
        return settings;
    }

    private static ResultSet row(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    @Test
    void hasVariableSavesFromTheVariableIdsAndSendsNoString() throws Exception {
        WiredConditionHasVariable box = new WiredConditionHasVariable(1, 1, boxBase(), "", 0, 0);
        withEmulator(() -> {
            assertTrue(box.saveData(settings(new int[] {1}, "", "-230")));
            WiredEditorState state = WiredEditorState.of(box, this.room);
            assertEquals("", state.stringParam());
            assertArrayEquals(new String[] {"-230"}, state.variableIds());
        });

        WiredConditionHasVariable legacy = new WiredConditionNotHasVariable(2, 1, boxBase(), "", 0, 0);
        WiredConditionHasVariable stored = new WiredConditionHasVariable(3, 1, boxBase(), "", 0, 0);
        withEmulator(() -> {
            assertTrue(legacy.saveData(settings(new int[] {1}, "internal:@level\t{}")));
            assertArrayEquals(new String[] {"-210"}, legacy.officialVariableIds());
            stored.loadWiredData(row(legacy.getWiredData()), null);
            assertArrayEquals(new String[] {"-210"}, stored.officialVariableIds());
        });
    }

    @Test
    void valueMatchSendsAirsSevenIntsVariableIdsAndTheReferenceHolderInSlotOne() throws Exception {
        WiredConditionVariableValueMatch box = new WiredConditionVariableValueMatch(4, 1, boxBase(), "", 0, 0);
        WiredSettings settings = settings(new int[] {1, 1, 1, 0, 0, 1, 0}, "", "-210", "-211");
        settings.setUserSourceTypes(new int[] {0, 200});
        withEmulator(() -> {
            assertTrue(box.saveData(settings));
            WiredEditorState state = WiredEditorState.of(box, this.room);
            assertArrayEquals(new int[] {1, 1, 1, 0, 0, 1, 0}, state.intParams());
            assertEquals("", state.stringParam());
            assertArrayEquals(new String[] {"-210", "-211"}, state.variableIds());
            assertArrayEquals(new int[] {0, 200}, state.userSources());
        });
    }

    @Test
    void valueMatchStillReadsTheEarlierNineIntsAndTokensInTheString() throws Exception {
        WiredConditionVariableValueMatch legacy = new WiredConditionVariableValueMatch(5, 1, boxBase(), "", 0, 0);
        WiredConditionVariableValueMatch stored = new WiredConditionVariableValueMatch(6, 1, boxBase(), "", 0, 0);
        withEmulator(() -> {
            assertTrue(legacy.saveData(settings(
                    new int[] {1, 1, 1, 0, 0, 1, 0, 200, 0}, "internal:@level\tinternal:@achievement_score\t\t")));
            assertArrayEquals(new String[] {"-210", "-211"}, legacy.officialVariableIds());
            assertArrayEquals(new int[] {0, 200}, legacy.officialUserSources());
            stored.loadWiredData(row(legacy.getWiredData()), null);
            assertArrayEquals(new String[] {"-210", "-211"}, stored.officialVariableIds());
            assertEquals(legacy.getWiredData(), stored.getWiredData());
        });
    }

    @Test
    void ageMatchSendsTheVariableAndTheDurationVariableAsVariableIds() throws Exception {
        WiredConditionVariableAgeMatch box = new WiredConditionVariableAgeMatch(7, 1, boxBase(), "", 0, 0);
        withEmulator(() -> {
            assertTrue(box.saveData(settings(new int[] {1, 2, 0, 0, 0, 1, 1, -10}, "", "user:40", "room:41")));
            WiredEditorState state = WiredEditorState.of(box, this.room);
            assertEquals("", state.stringParam());
            assertArrayEquals(new String[] {"user:40", "room:41"}, state.variableIds());
        });

        WiredConditionVariableAgeMatch legacy = new WiredConditionVariableAgeMatch(8, 1, boxBase(), "", 0, 0);
        withEmulator(() ->
                assertTrue(legacy.saveData(settings(new int[] {1, 2, 0, 0, 0, 1, 1, -10}, "custom:40\t41"))));
        assertArrayEquals(new String[] {"user:40", "room:41"}, legacy.officialVariableIds());
    }
}
