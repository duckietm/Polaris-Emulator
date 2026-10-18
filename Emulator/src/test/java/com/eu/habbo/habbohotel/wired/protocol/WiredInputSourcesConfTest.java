package com.eu.habbo.habbohotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

/** The official input sources conf a box's editor definition carries. */
class WiredInputSourcesConfTest {

    @Test
    void writesAllowedSlotsDefaultsAndTheWallFlagInTheOfficialOrder() {
        ServerMessage out = new ServerMessage(1);
        WiredInputSourcesConf.forInteraction("wf_act_send_signal").write(out);

        ByteBuf in = out.get();
        in.skipBytes(6);
        int[][] furni = readSlots(in);
        int[][] users = readSlots(in);
        int[] furniDefaults = readInts(in);
        int[] userDefaults = readInts(in);
        boolean wall = in.readBoolean();

        assertEquals(2, furni.length);
        assertArrayEquals(new int[] {0, 100, 101, 200, 201}, furni[0]);
        assertArrayEquals(new int[] {0, 100, 101, 200, 201}, furni[1]);
        assertEquals(1, users.length);
        assertArrayEquals(new int[] {0, 200, 201}, users[0]);
        assertArrayEquals(new int[] {100, 200}, furniDefaults);
        assertArrayEquals(new int[] {200}, userDefaults);
        assertFalse(wall);
        assertEquals(0, in.readableBytes());
    }

    @Test
    void anUnknownBoxSendsAnEmptyConf() {
        ServerMessage out = new ServerMessage(1);
        WiredInputSourcesConf.forInteraction("wf_act_toggle_state").write(out);

        ByteBuf in = out.get();
        in.skipBytes(6);
        for (int i = 0; i < 4; i++) assertEquals(0, in.readInt());
        assertFalse(in.readBoolean());
        assertEquals(0, in.readableBytes());
    }

    @Test
    void looksUpTheBoxByItsInteractionType() {
        HabboItem box = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(box.getBaseItem()).thenReturn(base);
        when(base.getInteractionType()).thenReturn(new ItemInteraction("wf_act_change_var_val", HabboItem.class));

        WiredInputSourcesConf conf = WiredInputSourcesConf.of(box);
        assertTrue(conf.allowWallFurni());
        assertArrayEquals(new int[] {0, 200}, conf.defaultUsers());

        when(base.getInteractionType()).thenReturn(null);
        assertSame(WiredInputSourcesConf.NONE, WiredInputSourcesConf.of(box));
        assertSame(WiredInputSourcesConf.NONE, WiredInputSourcesConf.of(null));
    }

    @Test
    void triggersOnlyOfferTheirOwnPickSources() {
        WiredInputSourcesConf stateChanged = WiredInputSourcesConf.forInteraction("wf_trg_state_changed");

        assertArrayEquals(new int[] {100, 200}, stateChanged.allowedFurni()[0]);
        assertEquals(0, stateChanged.allowedUsers().length);
        assertArrayEquals(new int[] {100}, stateChanged.defaultFurni());
    }

    private static int[][] readSlots(ByteBuf in) {
        int[][] slots = new int[in.readInt()][];
        for (int i = 0; i < slots.length; i++) slots[i] = readInts(in);
        return slots;
    }

    private static int[] readInts(ByteBuf in) {
        int[] values = new int[in.readInt()];
        for (int i = 0; i < values.length; i++) values[i] = in.readInt();
        return values;
    }
}
