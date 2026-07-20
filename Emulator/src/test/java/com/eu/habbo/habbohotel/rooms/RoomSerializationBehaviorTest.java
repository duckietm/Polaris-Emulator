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

    @Test
    void publicRoomSerialisesTheAnonymousOwnerWireShape() throws Exception {
        // Covers the isPublicRoom() branch: owner id is masked to 0 with an empty owner name,
        // the private (8) and promoted (4) flags are both absent, and no promotion trailer follows.
        Room room = RoomTestBuilder.room(9, 7)
                .field("name", "Public")
                .field("ownerName", "owner")
                .field("description", "Lobby")
                .field("state", RoomState.OPEN)
                .field("usersMax", 50)
                .field("score", 5)
                .field("category", 1)
                .field("tags", "")
                .field("publicRoom", true)
                .build();

        ServerMessage message = new ServerMessage(123);
        room.serialize(message);
        ByteBuf packet = message.get();

        int frameLength = packet.readInt();
        assertEquals(packet.readableBytes(), frameLength);
        assertEquals(123, packet.readUnsignedShort());
        assertEquals(9, packet.readInt());
        assertEquals("Public", readString(packet));
        assertEquals(0, packet.readInt(), "Public rooms mask the owner id");
        assertEquals("", readString(packet), "Public rooms mask the owner name");
        assertEquals(RoomState.OPEN.getState(), packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(50, packet.readInt());
        assertEquals("Lobby", readString(packet));
        assertEquals(0, packet.readInt());
        assertEquals(5, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(0, packet.readInt(), "No tags");
        assertEquals(0, packet.readInt(), "No guild/promoted/private flags for a public room");
        assertEquals(0, packet.readableBytes(), "Public non-promoted room writes no trailing fields");
    }

    @Test
    void expiredPromotionIsNormalisedOutOfTheWireShape() throws Exception {
        // Characterises the facade split: a stale promoted flag is normalised by isPromoted() during
        // serialize(), so an expired promotion sets neither the promoted flag bit nor its trailer —
        // keeping the flag and trailer consistent (no positional field-shift on the client).
        Room room = RoomTestBuilder.room(41, 7)
                .field("name", "Room name")
                .field("ownerName", "owner")
                .field("description", "desc")
                .field("state", RoomState.OPEN)
                .field("usersMax", 25)
                .field("score", 0)
                .field("category", 0)
                .field("tags", "")
                .field("publicRoom", false)
                .build();
        RoomPromotion expired = new RoomPromotion(
                room,
                "title",
                "description",
                Emulator.getIntUnixTimestamp() - 3600,
                Emulator.getIntUnixTimestamp() - 7200,
                1);
        setField(room.getPromotionManager(), "promotion", expired);
        room.getPromotionManager().setPromoted(true); // stale flag; serialize() must normalise it away

        ServerMessage message = new ServerMessage(123);
        room.serialize(message);
        ByteBuf packet = message.get();

        packet.readInt(); // frame length
        packet.readUnsignedShort(); // header
        packet.readInt(); // id
        readString(packet); // name
        packet.readInt(); // owner id
        readString(packet); // owner name
        packet.readInt(); // state
        packet.readInt(); // reserved 0
        packet.readInt(); // usersMax
        readString(packet); // description
        packet.readInt(); // reserved 0
        packet.readInt(); // score
        packet.readInt(); // reserved 0
        packet.readInt(); // category
        assertEquals(0, packet.readInt(), "No tags");
        assertEquals(8, packet.readInt(), "Private flag only — the expired promotion clears bit 4");
        assertEquals(0, packet.readableBytes(), "An expired promotion writes no promotion trailer");
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
