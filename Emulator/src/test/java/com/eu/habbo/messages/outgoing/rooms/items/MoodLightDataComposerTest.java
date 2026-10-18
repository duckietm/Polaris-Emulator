package com.eu.habbo.messages.outgoing.rooms.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomMoodlightData;
import io.netty.buffer.ByteBuf;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoodLightDataComposerTest {
    @Test
    void endsWithTheOnStateAndTheDimmerItemId() {
        List<RoomMoodlightData> presets = List.of(
                new RoomMoodlightData(1, false, true, "#74F5F5", 255),
                new RoomMoodlightData(2, true, false, "#0053F7", 200),
                new RoomMoodlightData(3, false, true, "#000000", 255));
        ByteBuf packet = new MoodLightDataComposer(presets, true, 812).compose().get();
        packet.skipBytes(6);

        assertEquals(3, packet.readInt());
        assertEquals(2, packet.readInt());
        for (int i = 0; i < 3; i++) {
            packet.readInt();
            packet.readInt();
            packet.skipBytes(packet.readUnsignedShort());
            packet.readInt();
        }
        assertTrue(packet.readBoolean());
        assertEquals(812, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void readsTheOnStateFromTheDimmerExtradata() {
        assertTrue(MoodLightDataComposer.isEnabled("2,1,2,#FF00FF,255"));
        assertFalse(MoodLightDataComposer.isEnabled("1,1,2,#FF00FF,255"));
        assertFalse(MoodLightDataComposer.isEnabled(""));
        assertFalse(MoodLightDataComposer.isEnabled("2,x,2,#FF00FF,255"));
        assertFalse(MoodLightDataComposer.isEnabled(null));
    }
}
