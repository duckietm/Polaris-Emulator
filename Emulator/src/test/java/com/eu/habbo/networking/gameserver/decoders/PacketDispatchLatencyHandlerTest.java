package com.eu.habbo.networking.gameserver.decoders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.monitoring.PacketDispatchLatencyMetrics;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PacketDispatchLatencyHandlerTest {

    @BeforeEach
    void resetMetrics() {
        PacketDispatchLatencyMetrics.snapshot();
    }

    @Test
    void recordsTimeBetweenIoMarkerAndDispatchHandler() {
        long step = TimeUnit.MILLISECONDS.toNanos(3);
        AtomicLong clock = new AtomicLong(TimeUnit.MILLISECONDS.toNanos(1));
        EmbeddedChannel channel = new EmbeddedChannel(
                new PacketDispatchMarker(() -> clock.getAndAdd(step)),
                new PacketDispatchLatencyHandler(() -> clock.getAndAdd(step)));
        ClientMessage message = new ClientMessage(7, Unpooled.buffer(1).writeByte(1));
        try {
            assertTrue(channel.writeInbound(message));
            assertSame(message, channel.readInbound());

            PacketDispatchLatencyMetrics.Snapshot snapshot = PacketDispatchLatencyMetrics.snapshot();
            assertEquals(1, snapshot.samples());
            assertEquals(3D, snapshot.averageMs(), 0.001);
        } finally {
            message.release();
            channel.finishAndReleaseAll();
        }
    }
}
