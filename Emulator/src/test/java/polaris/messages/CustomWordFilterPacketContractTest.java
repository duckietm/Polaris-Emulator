package polaris.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.RoomChatManager;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.UserWordFilter;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.rooms.users.RoomUserTalkComposer;
import polaris.messages.outgoing.users.CustomWordFilterModifyResultComposer;
import polaris.messages.outgoing.users.CustomWordFilterWordsComposer;

/** Wire contract of the AIR 13 personal word filter (class_1956 header table). */
class CustomWordFilterPacketContractTest {

    @Test
    void headersMatchTheOfficialClient() {
        assertEquals(145, Incoming.RequestCustomWordFilterEvent);
        assertEquals(68, Incoming.AddCustomWordFilterWordEvent);
        assertEquals(1996, Incoming.RemoveCustomWordFilterWordEvent);
        assertEquals(3883, Outgoing.CustomWordFilterWordsComposer);
        assertEquals(3333, Outgoing.CustomWordFilterModifyResultComposer);
    }

    @Test
    void wordListIsACountedStringBlock() {
        ByteBuf packet = new CustomWordFilterWordsComposer(List.of("pippo", "pluto"))
                .compose()
                .get();
        packet.skipBytes(4);

        assertEquals(3883, packet.readShort());
        assertEquals(2, packet.readInt());
        assertEquals("pippo", readString(packet));
        assertEquals("pluto", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void modifyResultCarriesTheResultCodeThenTheWord() {
        ByteBuf packet = new CustomWordFilterModifyResultComposer(CustomWordFilterModifyResultComposer.REMOVED, "pippo")
                .compose()
                .get();
        packet.skipBytes(4);

        assertEquals(3333, packet.readShort());
        assertEquals(3, packet.readInt());
        assertEquals("pippo", readString(packet));
        assertFalse(packet.isReadable());
    }

    @Test
    void recipientsWithAMatchingPersonalFilterGetTheirOwnMaskedPacket() {
        RoomChatMessage chat = message("ciao Pippo");
        ServerMessage shared = new RoomUserTalkComposer(chat).compose();
        User speaker = userWithFilter();
        User listener = userWithFilter("pippo");
        User bystander = userWithFilter("pluto");

        assertSame(shared, RoomChatManager.chatPacketFor(speaker, speaker, chat, shared, RoomUserTalkComposer::new));
        assertSame(shared, RoomChatManager.chatPacketFor(bystander, speaker, chat, shared, RoomUserTalkComposer::new));

        ServerMessage personal =
                RoomChatManager.chatPacketFor(listener, speaker, chat, shared, RoomUserTalkComposer::new);
        assertNotSame(shared, personal);
        ByteBuf packet = personal.get();
        packet.skipBytes(6);
        assertEquals(5, packet.readInt());
        assertEquals("ciao bobba", readString(packet));
        // the shared packet and the original message are untouched
        assertEquals("ciao Pippo", chat.getMessage());
    }

    private static User userWithFilter(String... words) {
        UserWordFilter filter = new UserWordFilter();
        for (String word : words) {
            filter.add(word);
        }
        UserStats stats = mock(UserStats.class);
        when(stats.getCustomWordFilter()).thenReturn(filter);
        User user = mock(User.class);
        when(user.getUserStats()).thenReturn(stats);
        return user;
    }

    private static RoomChatMessage message(String text) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(5);
        return new RoomChatMessage(text, unit, RoomChatMessageBubbles.NORMAL);
    }

    private static String readString(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
