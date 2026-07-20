package com.eu.habbo.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

class SharedBroadcastFrameTest {

    @Test
    void retainedRecipientFramesSharePreparedStorage() throws Exception {
        ServerMessage message = new ServerMessage(456);
        message.appendInt(123);

        ServerMessageFrame.prepareBroadcast(message);
        ByteBuf first = ServerMessageFrame.retainedDuplicate(message);
        ByteBuf second = ServerMessageFrame.retainedDuplicate(message);
        try {
            assertTrue(ServerMessageFrame.isBroadcastPrepared(message));
            assertSame(first.unwrap(), second.unwrap());
            assertEquals(first.readableBytes() - 4, first.getInt(0));
            assertEquals(456, first.getUnsignedShort(4));
            assertEquals(123, first.getInt(6));

            first.readerIndex(6);
            assertEquals(0, second.readerIndex());
        } finally {
            first.release();
            second.release();
        }
    }

    @Test
    void appendAfterPreparationUpdatesTheSharedFrame() {
        ServerMessage message = new ServerMessage(456);
        ServerMessageFrame.prepareBroadcast(message);
        message.appendString("later");

        ByteBuf frame = ServerMessageFrame.retainedDuplicate(message);
        try {
            assertEquals(frame.readableBytes() - 4, frame.getInt(0));
            assertEquals("later", readString(frame, 6));
        } finally {
            frame.release();
        }
    }

    private static String readString(ByteBuf frame, int index) {
        int length = frame.getUnsignedShort(index);
        return frame.toString(index + 2, length, java.nio.charset.StandardCharsets.UTF_8);
    }
}
