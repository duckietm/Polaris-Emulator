package com.eu.habbo.messages.outgoing.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BuildersClubPlacementWarningComposerTest {
    @Test
    void floorWarningEchoesThePlacementWithPositionAndDirection() {
        ByteBuf packet = BuildersClubPlacementWarningComposer.floor(12, 345, "", 4, 7, 2)
                .compose()
                .get();
        packet.skipBytes(4);

        assertEquals(Outgoing.BuildersClubPlacementWarningComposer, packet.readShort());
        assertEquals(BuildersClubPlacementWarningComposer.TYPE_FLOOR, packet.readInt());
        assertEquals(12, packet.readInt());
        assertEquals(345, packet.readInt());
        assertEquals("", readString(packet));
        assertEquals(4, packet.readInt());
        assertEquals(7, packet.readInt());
        assertEquals(2, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void wallWarningEchoesTheWallLocation() {
        ByteBuf packet = BuildersClubPlacementWarningComposer.wall(-1, 99, "255,0,0", ":w=1,2 l=3,4 r")
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(BuildersClubPlacementWarningComposer.TYPE_WALL, packet.readInt());
        assertEquals(-1, packet.readInt());
        assertEquals(99, packet.readInt());
        assertEquals("255,0,0", readString(packet));
        assertEquals(":w=1,2 l=3,4 r", readString(packet));
        assertFalse(packet.isReadable());
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        byte[] bytes = new byte[length];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
