package com.eu.habbo.networking.rconserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RCONServerHandlerWriteTest {

    @Test
    void responseUsesItsExactUtf8Bytes() {
        ByteBuf response = RCONServerHandler.responseBuffer("{\"message\":\"på gensyn\"}");
        try {
            assertEquals("{\"message\":\"på gensyn\"}", response.toString(StandardCharsets.UTF_8));
        } finally {
            response.release();
        }
    }

    @Test
    void connectionClosesOnlyAfterTheResponseWriteCompletes() {
        DelayedWriteHandler delayedWrite = new DelayedWriteHandler();
        ContextMarker marker = new ContextMarker();
        EmbeddedChannel channel = new EmbeddedChannel(delayedWrite, marker);

        RCONServerHandler.writeAndClose(
                channel.pipeline().context(marker), "{\"status\":1}");

        assertNotNull(delayedWrite.promise);
        assertTrue(channel.isOpen());
        assertEquals("{\"status\":1}",
                delayedWrite.message.toString(StandardCharsets.UTF_8));

        delayedWrite.promise.setSuccess();
        channel.runPendingTasks();

        assertFalse(channel.isOpen());
        delayedWrite.message.release();
        channel.finishAndReleaseAll();
    }

    private static final class ContextMarker extends io.netty.channel.ChannelInboundHandlerAdapter {
    }

    private static final class DelayedWriteHandler extends ChannelOutboundHandlerAdapter {
        private ByteBuf message;
        private ChannelPromise promise;

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            this.message = (ByteBuf) message;
            this.promise = promise;
        }
    }
}
