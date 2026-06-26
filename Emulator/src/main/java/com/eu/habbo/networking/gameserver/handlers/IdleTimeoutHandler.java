package com.eu.habbo.networking.gameserver.handlers;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.handshake.PingComposer;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class IdleTimeoutHandler extends ChannelDuplexHandler {
    private static final long MIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    private final long pingScheduleNanos;
    private final long pongTimeoutNanos;

    volatile ScheduledFuture<?> pingScheduleFuture;
    volatile boolean sentPing;
    volatile long lastPingTime; // in nanoseconds
    volatile long lastPongTime; // in nanoseconds

    private volatile int state; // 0 - none, 1 - initialized, 2 - destroyed

    public IdleTimeoutHandler(int pingScheduleSeconds, int pongTimeoutSeconds) {
        this.pingScheduleNanos = Math.max(MIN_TIMEOUT_NANOS, TimeUnit.SECONDS.toNanos(pingScheduleSeconds));
        this.pongTimeoutNanos = Math.max(MIN_TIMEOUT_NANOS, TimeUnit.SECONDS.toNanos(pongTimeoutSeconds));
    }

    private void initialize(ChannelHandlerContext ctx) {
        // Avoid the case where destroy() is called before scheduling timeouts.
        // See: https://github.com/netty/netty/issues/143
        switch (state) {
            case 1, 2 -> {
                return;
            }
        }

        state = 1;

        lastPongTime = System.nanoTime();
        if (pingScheduleNanos > 0) {
            pingScheduleFuture = ctx.executor().schedule(new PingScheduledTask(ctx), pingScheduleNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void destroy() {
        state = 2;

        if (pingScheduleFuture != null) {
            pingScheduleFuture.cancel(false);
            pingScheduleFuture = null;
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
            // channelActvie() event has been fired already, which means this.channelActive() will
            // not be invoked. We have to initialize here instead.
            initialize(ctx);
        } else {
            // channelActive() event has not been fired yet.  this.channelActive() will be invoked
            // and initialization will occur there.
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        destroy();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        // Initialize early if channel is active already.
        if (ctx.channel().isActive()) {
            initialize(ctx);
        }
        super.channelRegistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // This method will be invoked only if this handler was added
        // before channelActive() event is fired.  If a user adds this handler
        // after the channelActive() event, initialize() will be called by beforeAdd().
        initialize(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        destroy();
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // check if its a pong message
        if (msg instanceof ClientMessage packet) {
            if (packet.getMessageId() == Incoming.PongEvent) {
                this.lastPongTime = System.nanoTime();

                final GameClient client = ctx.channel().attr(GameServerAttributes.CLIENT).get();
                if (client != null) {
                    if (sentPing) {
                        sentPing = false;
                        client.getLatencyTracker().update(lastPongTime - lastPingTime);
                    }
                }
            }
        }
        super.channelRead(ctx, msg);
    }

    private final class PingScheduledTask implements Runnable {
        private final ChannelHandlerContext ctx;

        public PingScheduledTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }

            long currentTime = System.nanoTime();
            if (currentTime - lastPongTime > pongTimeoutNanos) {
                ctx.close();// add a promise here ?
                return;
            }

            final GameClient client = ctx.channel().attr(GameServerAttributes.CLIENT).get();
            if (client != null) {
                lastPingTime = System.nanoTime();
                sentPing = true;
                client.sendResponse(new PingComposer());
            }

            pingScheduleFuture = ctx.executor().schedule(this, pingScheduleNanos, TimeUnit.NANOSECONDS);
        }
    }
}
