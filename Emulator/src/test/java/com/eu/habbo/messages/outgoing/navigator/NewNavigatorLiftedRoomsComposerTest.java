package com.eu.habbo.messages.outgoing.navigator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.eu.habbo.habbohotel.navigation.NavigatorLiftedRoom;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewNavigatorLiftedRoomsComposerTest {
    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    @Test
    void writesEachLiftedRoomInTheOrderAirReadsIt() {
        ByteBuf packet = new NewNavigatorLiftedRoomsComposer(List.of(
                        new NavigatorLiftedRoom(12, 3, "officialrooms_hq/lobby.png", "Welcome Lobby"),
                        new NavigatorLiftedRoom(40, 0, "", "Pool")))
                .compose()
                .get();
        try {
            packet.skipBytes(4);
            assertEquals(Outgoing.NewNavigatorLiftedRoomsComposer, packet.readShort());
            assertEquals(2, packet.readInt());
            assertEquals(12, packet.readInt());
            assertEquals(3, packet.readInt());
            assertEquals("officialrooms_hq/lobby.png", readString(packet));
            assertEquals("Welcome Lobby", readString(packet));
            assertEquals(40, packet.readInt());
            assertEquals(0, packet.readInt());
            assertEquals("", readString(packet));
            assertEquals("Pool", readString(packet));
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void sendsAnEmptyListWhenNoRoomsAreLifted() {
        ByteBuf packet = new NewNavigatorLiftedRoomsComposer(null).compose().get();
        try {
            packet.skipBytes(6);
            assertEquals(0, packet.readInt());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }
}
