package com.eu.habbo.networking.gameserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void nonBinaryFramesAreRejectedAtTheGameProtocolBoundary() {
        List<Supplier<WebSocketFrame>> unexpectedFrames = List.of(
                () -> new TextWebSocketFrame("text"),
                () -> new ContinuationWebSocketFrame(
                        Unpooled.copiedBuffer("continuation", StandardCharsets.UTF_8)),
                PingWebSocketFrame::new,
                PongWebSocketFrame::new,
                CloseWebSocketFrame::new);

        for (Supplier<WebSocketFrame> frameFactory : unexpectedFrames) {
            assertRejected(frameFactory.get());
        }
    }

    @Test
    void unaggregatedBinaryFragmentsAreRejected() {
        assertRejected(new BinaryWebSocketFrame(
                false, 0, Unpooled.copiedBuffer("fragment", StandardCharsets.UTF_8)));
    }

    private static void assertRejected(WebSocketFrame frame) {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketCodec());
        try {
            assertFalse(channel.writeInbound(frame));
            assertFalse(channel.isOpen());
            assertNull(channel.readInbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
