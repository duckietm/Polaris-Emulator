package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredExtraDailyTaskTest {

    private static WiredExtraDailyTask box() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(5300);
        return new WiredExtraDailyTask(30, 1, base, "", 0, 0);
    }

    @Test
    void theTargetAndTaskNameAreSavedAndSentBack() {
        WiredExtraDailyTask box = box();

        assertTrue(box.saveData(new WiredSettings(new int[] {-4}, "Water the plants\r\n", new int[0], 0), null));
        assertEquals(0, box.getTargetValue());
        assertEquals("Water the plants", box.getTaskName());

        box.saveData(new WiredSettings(new int[] {12}, "chores\t" + "x".repeat(150), new int[0], 0), null);
        assertEquals(12, box.getTargetValue());
        assertEquals(WiredExtraDailyTask.MAX_TASK_NAME_LENGTH, box.getTaskName().length());

        ServerMessage message = new ServerMessage(1);
        box.serializeWiredData(message, null);
        ByteBuf buffer = message.get();
        try {
            buffer.readInt();
            buffer.readShort();
            buffer.readByte();
            for (int i = 0; i < 4; i++) buffer.readInt();
            byte[] text = new byte[buffer.readShort()];
            buffer.readBytes(text);
            // AIR: name<TAB>task and no int params.
            assertEquals("chores\t" + "x".repeat(100), new String(text, StandardCharsets.UTF_8));
            assertEquals(0, buffer.readInt());
            buffer.readInt();
            assertEquals(WiredExtraDailyTask.CODE, buffer.readInt());
        } finally {
            buffer.release();
        }
    }

    @Test
    void theConfigurationSurvivesReloadAndPickupClearsIt() throws Exception {
        WiredExtraDailyTask saved = box();
        saved.saveData(new WiredSettings(new int[] {5}, "Laps", new int[0], 0), null);

        WiredExtraDailyTask loaded = box();
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(saved.getWiredData());
        loaded.loadWiredData(set, null);
        assertEquals(5, loaded.getTargetValue());
        assertEquals("Laps", loaded.getTaskName());

        loaded.onPickUp();
        assertEquals(0, loaded.getTargetValue());
        assertEquals("", loaded.getTaskName());
    }

    @Test
    void itExposesTheQuestSubVariablesAgainstItsTarget() {
        WiredExtraDailyTask box = box();
        box.saveData(new WiredSettings(new int[] {4}, "", new int[0], 0), null);

        assertEquals(List.of(0, 1, 2, 3, 4), box.getSelectedSubvariables());
        assertEquals("progress", box.subvariableKey(WiredExtraQuest.SUB_PROGRESS));
        assertEquals(3, box.derive(WiredExtraQuest.SUB_PROGRESS, 3));
        assertEquals(4, box.derive(WiredExtraQuest.SUB_TARGET, 3));
        assertEquals(0, box.derive(WiredExtraQuest.SUB_IS_COMPLETE, 3));
        assertEquals(1, box.derive(WiredExtraQuest.SUB_IS_COMPLETE, 4));
        assertEquals(75, box.derive(WiredExtraQuest.SUB_PERCENT, 3));
        assertEquals(1, box.derive(WiredExtraQuest.SUB_REMAINING, 3));
        assertNull(box.derive(WiredExtraQuest.SUB_PROGRESS, null));
    }
}
