package com.eu.habbo.messages.outgoing.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.guides.GuardianTicket;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;

class BullyReportRequestComposerTest {
    private static ByteBuf body(BullyReportRequestComposer composer) {
        ByteBuf packet = composer.compose().get();
        packet.skipBytes(4);
        assertEquals(3463, packet.readShort());
        return packet;
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    @Test
    void anAnswerWithoutTicketIsTheStatusAlone() {
        ByteBuf packet = body(new BullyReportRequestComposer(BullyReportRequestComposer.TOO_RECENT));
        try {
            assertEquals(BullyReportRequestComposer.TOO_RECENT, packet.readInt());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void aPendingBullyReportIsTypeThreeWithTheReportedUserAndRoom() {
        GuardianTicket ticket = mock(GuardianTicket.class);
        when(ticket.getDate()).thenReturn(new Date(System.currentTimeMillis() - 65_000L));
        when(ticket.getReportedName()).thenReturn("Bob");
        when(ticket.getReportedFigure()).thenReturn("hd-180-1");
        when(ticket.getRoomName()).thenReturn("The Lounge");

        ByteBuf packet = body(new BullyReportRequestComposer(BullyReportRequestComposer.ONGOING_HELPER_CASE, ticket));
        try {
            assertEquals(BullyReportRequestComposer.ONGOING_HELPER_CASE, packet.readInt());
            assertEquals(BullyReportRequestComposer.TYPE_BULLY_REPORT, packet.readInt());
            int secondsAgo = packet.readInt();
            assertTrue(secondsAgo >= 64 && secondsAgo <= 70, "seconds ago: " + secondsAgo);
            assertFalse(packet.readBoolean());
            assertEquals("Bob", readString(packet));
            assertEquals("hd-180-1", readString(packet));
            assertEquals("The Lounge", readString(packet));
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void aPendingCaseWithoutTicketFallsBackToTooRecent() {
        ByteBuf packet = body(new BullyReportRequestComposer(BullyReportRequestComposer.ONGOING_HELPER_CASE));
        try {
            assertEquals(BullyReportRequestComposer.TOO_RECENT, packet.readInt());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }
}
