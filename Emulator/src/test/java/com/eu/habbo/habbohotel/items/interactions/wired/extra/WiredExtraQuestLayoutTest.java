package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** AIR's quest, quest chain and daily task variables: {@code name<TAB>code} and no int params. */
class WiredExtraQuestLayoutTest {

    private static ResultSet stored(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    @Test
    void aQuestSavesAndSendsTheNameAndTheQuestCode() throws Exception {
        WiredExtraQuest quest = new WiredExtraQuest(81, 1, mock(Item.class), "", 0, 0);
        quest.saveData(new WiredSettings(new int[0], "my quest\tQUEST_1234", new int[0], 0), null);

        assertEquals("my_quest", quest.getVariableName());
        assertEquals("QUEST_1234", quest.getQuestName());
        LegacyDefinition definition = LegacyDefinition.of(quest, null);
        assertEquals("my_quest\tQUEST_1234", definition.stringParam());
        assertArrayEquals(new int[0], definition.intParams());
        assertEquals(WiredExtraQuest.CODE, definition.code());

        WiredExtraQuest reloaded = new WiredExtraQuest(81, 1, mock(Item.class), "", 0, 0);
        reloaded.loadWiredData(stored(quest.getWiredData()), null);
        assertEquals("my_quest", reloaded.getVariableName());
        assertEquals("QUEST_1234", reloaded.getQuestName());
    }

    @Test
    void anOldQuestKeepsItsTargetThroughAnAirSave() throws Exception {
        WiredExtraQuestChain chain = new WiredExtraQuestChain(82, 1, mock(Item.class), "", 0, 0);
        chain.loadWiredData(stored("{\"targetValue\":6}"), null);

        assertEquals(6, chain.getTargetValue());
        assertEquals("\t", LegacyDefinition.of(chain, null).stringParam());

        chain.saveData(new WiredSettings(new int[0], "steps\tCHAIN_9", new int[0], 0), null);
        assertEquals(6, chain.getTargetValue());
        assertEquals(100, chain.derive(WiredExtraQuestChain.SUB_PERCENT, 6));

        String longCode = "q".repeat(600);
        chain.saveData(new WiredSettings(new int[0], "steps\t" + longCode, new int[0], 0), null);
        assertEquals(WiredExtraQuestBase.MAX_QUEST_NAME_LENGTH, chain.getQuestName().length());
    }

    @Test
    void anOldDailyTaskKeepsItsTaskAndGetsAName() throws Exception {
        WiredExtraDailyTask task = new WiredExtraDailyTask(83, 1, mock(Item.class), "", 0, 0);
        task.loadWiredData(stored("{\"targetValue\":3,\"taskName\":\"Laps\"}"), null);

        assertEquals("\tLaps", LegacyDefinition.of(task, null).stringParam());

        task.saveData(new WiredSettings(new int[0], "laps_today\t1234", new int[0], 0), null);
        assertEquals("laps_today", task.getVariableName());
        assertEquals("1234", task.getTaskName());
        assertEquals(3, task.getTargetValue());
        assertEquals("laps_today\t1234", LegacyDefinition.of(task, null).stringParam());
    }
}
