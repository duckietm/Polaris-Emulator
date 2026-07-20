package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RoomSerializationBehaviorTest {

    @Test
    void privatePromotedRoomKeepsTheEstablishedNavigatorWireShape() throws Exception {
        Room room = RoomTestBuilder.room(41, 7)
                .field("name", "Room name")
                .field("ownerName", "owner")
                .field("description", "Room description")
                .field("state", RoomState.PASSWORD)
                .field("usersMax", 25)
                .field("score", 88)
                .field("category", 3)
                .field("tags", "retro;;polaris;")
                .field("publicRoom", false)
                .build();
        RoomPromotion promotion = new RoomPromotion(
                room,
                "Promotion title",
                "Promotion description",
                Emulator.getIntUnixTimestamp() + 3600,
                Emulator.getIntUnixTimestamp(),
                1);
        setField(room.getPromotionManager(), "promotion", promotion);
        room.getPromotionManager().setPromoted(true);

        ServerMessage message = new ServerMessage(123);
        room.serialize(message);
        ByteBuf packet = message.get();

        int frameLength = packet.readInt();
        assertEquals(packet.readableBytes(), frameLength);
        assertEquals(123, packet.readUnsignedShort());
        assertEquals(41, packet.readInt());
        assertEquals("Room name", readString(packet));
        assertEquals(7, packet.readInt());
        assertEquals("owner", readString(packet));
        assertEquals(RoomState.PASSWORD.getState(), packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(25, packet.readInt());
        assertEquals("Room description", readString(packet));
        assertEquals(0, packet.readInt());
        assertEquals(88, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals(2, packet.readInt());
        assertEquals("retro", readString(packet));
        assertEquals("polaris", readString(packet));
        assertEquals(12, packet.readInt());
        assertEquals("Promotion title", readString(packet));
        assertEquals("Promotion description", readString(packet));
        int remainingMinutes = packet.readInt();
        assertTrue(remainingMinutes == 59 || remainingMinutes == 60);
        assertEquals(0, packet.readableBytes());
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
