package com.eu.habbo.networking.gameserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WebSocketCodecTest {

    @Test
    void binaryFramesExposeTheirExactPayload() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketCodec());
        channel.writeInbound(new BinaryWebSocketFrame(
                Unpooled.copiedBuffer("game-packet", StandardCharsets.UTF_8)));

        ByteBuf payload = channel.readInbound();
        try {
            assertEquals("game-packet", payload.toString(StandardCharsets.UTF_8));
        } finally {
            payload.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void outboundPayloadsRemainBinaryFrames() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketCodec());
        channel.writeOutbound(Unpooled.copiedBuffer("game-packet", StandardCharsets.UTF_8));

        BinaryWebSocketFrame frame = assertInstanceOf(BinaryWebSocketFrame.class, channel.readOutbound());
        try {
            assertEquals("game-packet", frame.content().toString(StandardCharsets.UTF_8));
        } finally {
            frame.release();
            channel.finishAndReleaseAll();
        }
    }
}
