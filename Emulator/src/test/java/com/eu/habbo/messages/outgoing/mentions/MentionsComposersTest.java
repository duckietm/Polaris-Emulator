package com.eu.habbo.messages.outgoing.mentions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.mentions.HabboMention;
import com.eu.habbo.habbohotel.mentions.MentionPage;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The server to client mention packets are not covered by the generated contract: their field order is pinned here. */
class MentionsComposersTest {
    private static HabboMention mention(int id) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(id);
        when(set.getInt("target_user_id")).thenReturn(1);
        when(set.getInt("sender_user_id")).thenReturn(7);
        when(set.getString("sender_username")).thenReturn("alice");
        when(set.getInt("room_id")).thenReturn(9);
        when(set.getString("room_name")).thenReturn("Lobby");
        when(set.getString("message")).thenReturn("hi @bob");
        when(set.getInt("mention_type")).thenReturn(0);
        when(set.getInt("timestamp")).thenReturn(1_700_000_000);
        when(set.getInt("read")).thenReturn(1);
        when(set.findColumn(anyString())).thenReturn(1);
        when(set.getString("sender_figure")).thenReturn("hd-180-1");
        return new HabboMention(set);
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static void assertMention(ByteBuf packet, int id) {
        assertEquals(id, packet.readInt());
        assertEquals(7, packet.readInt());
        assertEquals("alice", readString(packet));
        assertEquals("hd-180-1", readString(packet));
        assertEquals(9, packet.readInt());
        assertEquals("Lobby", readString(packet));
        assertEquals("hi @bob", readString(packet));
        assertEquals(0, packet.readInt());
        assertEquals(1_700_000_000, packet.readInt());
        assertTrue(packet.readBoolean());
    }

    @Test
    void theListWritesCountTheMentionsAndThenWhetherOlderOnesRemain() throws Exception {
        ByteBuf packet = new MentionsListComposer(List.of(mention(5), mention(4)), true)
                .compose()
                .get();
        try {
            packet.skipBytes(4);
            assertEquals(Outgoing.MentionsListComposer, packet.readShort());
            assertEquals(2, packet.readInt());
            assertMention(packet, 5);
            assertMention(packet, 4);
            assertTrue(packet.readBoolean());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void theOldConstructorStillWorksAndSaysNothingRemains() throws Exception {
        ByteBuf packet = new MentionsListComposer(List.of()).compose().get();
        try {
            packet.skipBytes(4);
            assertEquals(Outgoing.MentionsListComposer, packet.readShort());
            assertEquals(0, packet.readInt());
            assertFalse(packet.readBoolean());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void aPageHasTheSameBodyUnderItsOwnHeader() throws Exception {
        ByteBuf packet = new MentionsPageComposer(new MentionPage(List.of(mention(3)), false))
                .compose()
                .get();
        try {
            packet.skipBytes(4);
            assertEquals(Outgoing.MentionsPageComposer, packet.readShort());
            assertEquals(1, packet.readInt());
            assertMention(packet, 3);
            assertFalse(packet.readBoolean());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }
}
