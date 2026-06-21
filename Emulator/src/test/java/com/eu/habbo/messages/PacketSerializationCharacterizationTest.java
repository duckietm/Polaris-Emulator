package com.eu.habbo.messages;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for ClientMessage's defensive reads and ServerMessage's
 * framing. These pin down CURRENT, security-relevant behavior — in particular
 * that a bogus declared string length can neither throw nor desync the rest of
 * the packet — so future refactors must preserve it.
 */
class PacketSerializationCharacterizationTest {

    @Test
    void readStringClampsDeclaredLengthToAvailableBytes() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(100);                                    // claims 100 bytes...
        buf.writeBytes("hi".getBytes(StandardCharsets.UTF_8));  // ...but only 2 are present
        ClientMessage msg = new ClientMessage(1, buf);

        assertEquals("hi", msg.readString());                  // clamped, no exception
    }

    @Test
    void readStringReadsExactlyDeclaredLengthAndLeavesTrailer() {
        ByteBuf buf = Unpooled.buffer();
        byte[] hello = "hello".getBytes(StandardCharsets.UTF_8);
        buf.writeShort(hello.length);
        buf.writeBytes(hello);
        buf.writeBytes("MORE".getBytes(StandardCharsets.UTF_8));
        ClientMessage msg = new ClientMessage(1, buf);

        assertEquals("hello", msg.readString());
        assertEquals(4, msg.bytesAvailable());                 // "MORE" still buffered
    }

    @Test
    void exhaustedBufferReadsReturnDefaultsInsteadOfThrowing() {
        ClientMessage msg = new ClientMessage(7, Unpooled.EMPTY_BUFFER);

        assertEquals(7, msg.getMessageId());
        assertEquals(0, msg.bytesAvailable());
        assertEquals(0, msg.readShort());
        assertEquals(0, (int) msg.readInt());
        assertEquals("", msg.readString());
        assertFalse(msg.readBoolean());
    }

    @Test
    void readBooleanIsTrueOnlyForByteOne() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(1);
        buf.writeByte(0);
        buf.writeByte(2);
        ClientMessage msg = new ClientMessage(1, buf);

        assertTrue(msg.readBoolean());   // 1 -> true
        assertFalse(msg.readBoolean());  // 0 -> false
        assertFalse(msg.readBoolean());  // 2 -> false (only exactly 1 is true)
    }

    @Test
    void nullBufferBecomesEmpty() {
        ClientMessage msg = new ClientMessage(5, null);

        assertEquals(0, msg.bytesAvailable());
        assertEquals("", msg.readString());
    }

    @Test
    void serverMessageGetWritesLengthPrefixThenHeader() {
        ServerMessage message = new ServerMessage(3990);
        ByteBuf out = message.get();

        int length = out.readInt();   // bytes after the length field
        int header = out.readShort();
        assertEquals(2, length);      // header only, no body
        assertEquals(3990, header);
        assertEquals(0, out.readableBytes());
    }

    @Test
    void serverMessageLengthCountsAppendedBody() {
        ServerMessage message = new ServerMessage(10);
        message.appendInt(1);         // +4 bytes
        message.appendString("ab");   // +2 (length short) +2 (chars)
        ByteBuf out = message.get();

        int length = out.readInt();
        int header = out.readShort();
        int intField = out.readInt();
        int strLen = out.readShort();
        byte[] s = new byte[strLen];
        out.readBytes(s);

        assertEquals(2 + 4 + 4, length); // header(2) + int(4) + string(4)
        assertEquals(10, header);
        assertEquals(1, intField);
        assertEquals(2, strLen);
        assertEquals("ab", new String(s, StandardCharsets.UTF_8));
    }
}
