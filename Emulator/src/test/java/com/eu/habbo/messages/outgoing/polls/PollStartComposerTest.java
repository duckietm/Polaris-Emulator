package com.eu.habbo.messages.outgoing.polls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.polls.Poll;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class PollStartComposerTest {
    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    @Test
    void offerSendsIdTypeHeadlineSummaryInTheClientsOrder() throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(5);
        when(set.getString("title")).thenReturn("Favourite colour?");
        when(set.getString("thanks_message")).thenReturn("Thanks!");
        when(set.getString("reward_badge")).thenReturn("");

        ByteBuf packet = new PollStartComposer(new Poll(set)).compose().get();
        try {
            packet.skipBytes(4);
            assertEquals(Outgoing.PollStartComposer, packet.readShort());
            assertEquals(5, packet.readInt());
            assertEquals("", readString(packet));
            assertEquals("Favourite colour?", readString(packet));
            assertEquals("", readString(packet));
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }
}
