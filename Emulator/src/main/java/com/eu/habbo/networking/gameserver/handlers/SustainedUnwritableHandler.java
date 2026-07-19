package com.eu.habbo.networking.gameserver.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class SustainedUnwritableHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SustainedUnwritableHandler.class);

    private final long timeoutNanos;
    private ScheduledFuture<?> closeFuture;

    public SustainedUnwritableHandler(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeoutNanos = unit.toNanos(timeout);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext context) {
        if (context.channel().isWritable()) {
            cancelClose();
        } else if (this.closeFuture == null) {
            this.closeFuture = context.executor().schedule(
                    () -> closeIfStillUnwritable(context),
                    this.timeoutNanos,
                    TimeUnit.NANOSECONDS);
        }
        context.fireChannelWritabilityChanged();
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        cancelClose();
        super.channelInactive(context);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext context) {
        cancelClose();
    }

    private void closeIfStillUnwritable(ChannelHandlerContext context) {
        this.closeFuture = null;
        if (!context.channel().isOpen() || context.channel().isWritable()) {
            return;
        }

        LOGGER.warn(
                "Disconnecting channel {} after {} ms of sustained unwritability; {} bytes must drain before writes resume",
                context.channel().remoteAddress(),
                TimeUnit.NANOSECONDS.toMillis(this.timeoutNanos),
                context.channel().bytesBeforeWritable());
        context.close();
    }

    private void cancelClose() {
        if (this.closeFuture != null) {
            this.closeFuture.cancel(false);
            this.closeFuture = null;
        }
    }
}
