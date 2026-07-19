package com.eu.habbo.networking.gameserver.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class SustainedUnwritableHandler extends ChannelInboundHandlerAdapter {

    private final long timeoutNanos;

    public SustainedUnwritableHandler(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeoutNanos = unit.toNanos(timeout);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext context) {
        context.fireChannelWritabilityChanged();
    }
}
