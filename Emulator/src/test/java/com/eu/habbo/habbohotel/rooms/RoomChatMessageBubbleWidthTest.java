package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTalkComposer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

/**
 * A wired message may ask for a bubble width of its own, and a wired bot may hide its name. The client
 * reads both as an optional tail of the chat packet (the width int, then the hide-name boolean), so a
 * plain message ends with -1 and false, and old clients that stop reading earlier lose nothing.
 */
class RoomChatMessageBubbleWidthTest {

    @Test
    void aPlainMessageEndsWithNoOverride() {
        assertEquals(-1, trailingInt(message()));
    }

    @Test
    void theOverrideTravelsAtTheEndOfThePacket() {
        RoomChatMessage message = message();
        message.setBubbleWidthOverride(2);

        assertEquals(2, trailingInt(message));
    }

    @Test
    void aPlainMessageKeepsTheName() {
        assertFalse(trailingBoolean(message()));
    }

    @Test
    void theHiddenNameTravelsAfterTheWidth() {
        RoomChatMessage message = message();
        message.setBubbleWidthOverride(1);
        message.setHideUsername(true);

        assertEquals(1, trailingInt(message));
        assertTrue(trailingBoolean(message));
    }

    @Test
    void theCopyKeepsTheHiddenName() {
        RoomChatMessage message = message();
        message.setHideUsername(true);

        assertTrue(new RoomChatMessage(message).isHideUsername());
    }

    @Test
    void aWidthThatNamesNothingIsNoOverride() {
        RoomChatMessage message = message();
        message.setBubbleWidthOverride(7);

        assertEquals(-1, message.getBubbleWidthOverride());
    }

    @Test
    void theCopyKeepsTheOverride() {
        RoomChatMessage message = message();
        message.setBubbleWidthOverride(0);

        assertEquals(0, new RoomChatMessage(message).getBubbleWidthOverride());
    }

    private static RoomChatMessage message() {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(5);
        return new RoomChatMessage("hello", unit, RoomChatMessageBubbles.NORMAL);
    }

    private static int trailingInt(RoomChatMessage message) {
        ByteBuf packet = new RoomUserTalkComposer(message).compose().get();
        try {
            return packet.getInt(packet.writerIndex() - 5);
        } finally {
            packet.release();
        }
    }

    private static boolean trailingBoolean(RoomChatMessage message) {
        ByteBuf packet = new RoomUserTalkComposer(message).compose().get();
        try {
            return packet.getByte(packet.writerIndex() - 1) == 1;
        } finally {
            packet.release();
        }
    }
}
