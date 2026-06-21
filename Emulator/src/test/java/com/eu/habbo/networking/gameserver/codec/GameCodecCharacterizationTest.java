package com.eu.habbo.networking.gameserver.codec;

import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.monitoring.EmulatorNetworkStats;
import com.eu.habbo.networking.gameserver.decoders.GameByteDecoder;
import com.eu.habbo.networking.gameserver.encoders.GameServerMessageEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the game packet codec — every inbound and outbound
 * packet passes through these classes. They lock in the CURRENT wire format
 * ([length:int][header:short][body] outbound; [header:short][body] inbound) so a
 * later refactor (Phase 1+) cannot silently change framing without a red test.
 *
 * Uses Netty's EmbeddedChannel so the real handlers run with no socket and no
 * Emulator/DB bootstrap.
 */
class GameCodecCharacterizationTest {

    @Test
    void decoderSplitsHeaderShortThenBodyRemainder() {
        EmbeddedChannel channel = new EmbeddedChannel(new GameByteDecoder());

        ByteBuf in = Unpooled.buffer();
        in.writeShort(3990);                    // header
        in.writeBytes(new byte[]{1, 2, 3, 4});  // body

        assertTrue(channel.writeInbound(in));
        ClientMessage msg = channel.readInbound();

        assertNotNull(msg);
        assertEquals(3990, msg.getMessageId());
        assertEquals(4, msg.bytesAvailable());

        byte[] body = new byte[msg.bytesAvailable()];
        msg.getBuffer().readBytes(body);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, body);

        assertFalse(channel.finish());
    }

    @Test
    void decoderHeaderOnlyFrameYieldsEmptyBody() {
        EmbeddedChannel channel = new EmbeddedChannel(new GameByteDecoder());

        ByteBuf in = Unpooled.buffer();
        in.writeShort(1000);

        assertTrue(channel.writeInbound(in));
        ClientMessage msg = channel.readInbound();

        assertNotNull(msg);
        assertEquals(1000, msg.getMessageId());
        assertEquals(0, msg.bytesAvailable());

        assertFalse(channel.finish());
    }

    @Test
    void encoderPrefixesFourByteLengthThenHeader() {
        long before = EmulatorNetworkStats.getOutgoingPackets();
        EmbeddedChannel channel = new EmbeddedChannel(new GameServerMessageEncoder());

        assertTrue(channel.writeOutbound(new ServerMessage(3))); // empty body
        ByteBuf out = channel.readOutbound();

        assertNotNull(out);
        int length = out.readInt();   // counts bytes AFTER the length field
        int header = out.readShort();
        assertEquals(2, length);      // just the 2-byte header
        assertEquals(3, header);
        assertEquals(0, out.readableBytes());
        assertEquals(before + 1, EmulatorNetworkStats.getOutgoingPackets());

        out.release();
        assertFalse(channel.finish());
    }

    @Test
    void encodeThenDecodeRoundTripPreservesHeaderAndBody() {
        EmbeddedChannel encoder = new EmbeddedChannel(new GameServerMessageEncoder());

        ServerMessage message = new ServerMessage(42);
        message.appendInt(7);
        message.appendString("hi");

        assertTrue(encoder.writeOutbound(message));
        ByteBuf encoded = encoder.readOutbound();
        encoded.skipBytes(4); // drop length prefix — the frame decoder strips it in production

        EmbeddedChannel decoder = new EmbeddedChannel(new GameByteDecoder());
        assertTrue(decoder.writeInbound(encoded));
        ClientMessage decoded = decoder.readInbound();

        assertNotNull(decoded);
        assertEquals(42, decoded.getMessageId());
        assertEquals(7, (int) decoded.readInt());
        assertEquals("hi", decoded.readString());

        assertFalse(encoder.finish());
        assertFalse(decoder.finish());
    }
}
