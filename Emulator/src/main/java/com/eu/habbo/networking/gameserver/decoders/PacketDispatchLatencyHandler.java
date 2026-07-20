package com.eu.habbo.networking.gameserver.decoders;

import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ClientMessageDispatchTiming;
import com.eu.habbo.monitoring.PacketDispatchLatencyMetrics;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.function.LongSupplier;

public final class PacketDispatchLatencyHandler extends ChannelInboundHandlerAdapter {

    private final LongSupplier nanoClock;

    public PacketDispatchLatencyHandler() {
        this(System::nanoTime);
    }

    PacketDispatchLatencyHandler(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        if (message instanceof ClientMessage clientMessage) {
            long enqueuedAt = ClientMessageDispatchTiming.takeEnqueuedAt(clientMessage);
            if (enqueuedAt != 0L) {
                PacketDispatchLatencyMetrics.record(this.nanoClock.getAsLong() - enqueuedAt);
            }
        }

        context.fireChannelRead(message);
    }
}
