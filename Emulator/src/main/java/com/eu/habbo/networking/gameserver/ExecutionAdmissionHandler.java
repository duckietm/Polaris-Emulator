package com.eu.habbo.networking.gameserver;

import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

final class ExecutionAdmissionHandler extends ChannelInboundHandlerAdapter {
    private static final byte[] BUSY_RESPONSE =
            "{\"error\":\"Server busy, try again shortly.\"}".getBytes(StandardCharsets.UTF_8);

    private final String targetHandlerName;
    private final Lane lane;
    private final ExecutionCapacityController capacityController;
    private final ExecutionBackpressureMetrics metrics;
    private final int perConnectionCapacity;
    private final int perConnectionLowWatermark;
    private final int pendingCapacity;
    private final long pauseTimeoutMillis;
    private final Deque<ClientMessage> pendingMessages = new ArrayDeque<>();

    private int inFlight;
    private boolean paused;
    private boolean affectedRecorded;
    private boolean autoReadWasEnabled;
    private long pauseStartedAtNanos;
    private ScheduledFuture<?> pauseDeadline;

    private ExecutionAdmissionHandler(
            String targetHandlerName,
            Lane lane,
            ExecutionCapacityController capacityController,
            ExecutionBackpressureMetrics metrics,
            int perConnectionCapacity,
            int perConnectionLowWatermark,
            int pendingCapacity,
            long pauseTimeoutMillis) {
        this.targetHandlerName = Objects.requireNonNull(targetHandlerName, "targetHandlerName");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.capacityController = Objects.requireNonNull(capacityController, "capacityController");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        if (perConnectionCapacity <= 0) {
            throw new IllegalArgumentException("Per-connection capacity must be positive");
        }
        if (perConnectionLowWatermark < 0 || perConnectionLowWatermark >= perConnectionCapacity) {
            throw new IllegalArgumentException("Per-connection low watermark is out of range");
        }
        if (pendingCapacity <= 0) {
            throw new IllegalArgumentException("Pending capacity must be positive");
        }
        if (pauseTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Pause timeout must be positive");
        }
        this.perConnectionCapacity = perConnectionCapacity;
        this.perConnectionLowWatermark = perConnectionLowWatermark;
        this.pendingCapacity = pendingCapacity;
        this.pauseTimeoutMillis = pauseTimeoutMillis;
    }

    static ExecutionAdmissionHandler forGamePackets(
            String targetHandlerName,
            ExecutionCapacityController capacityController,
            ExecutionBackpressureMetrics metrics,
            int perConnectionCapacity,
            int perConnectionLowWatermark,
            int pendingCapacity,
            long pauseTimeoutMillis) {
        return new ExecutionAdmissionHandler(
                targetHandlerName,
                Lane.GAME_PACKET,
                capacityController,
                metrics,
                perConnectionCapacity,
                perConnectionLowWatermark,
                pendingCapacity,
                pauseTimeoutMillis);
    }

    static ExecutionAdmissionHandler forBlockingHttp(
            String targetHandlerName,
            ExecutionCapacityController capacityController,
            ExecutionBackpressureMetrics metrics) {
        return new ExecutionAdmissionHandler(
                targetHandlerName, Lane.BLOCKING_HTTP, capacityController, metrics, 1, 0, 1, 1_000L);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!this.lane.accepts(msg)) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (this.lane == Lane.BLOCKING_HTTP) {
            admitHttp(ctx, msg);
            return;
        }

        ClientMessage message = (ClientMessage) msg;
        if (this.capacityController.isEnforcing() && this.inFlight >= this.perConnectionCapacity) {
            queuePacket(ctx, message);
            return;
        }

        if (this.capacityController.tryAcquire(this) == ExecutionCapacityController.Admission.SATURATED) {
            queuePacket(ctx, message);
            this.capacityController.registerWaiter(this, () -> scheduleDrain(ctx));
            return;
        }

        this.inFlight++;
        dispatch(ctx, message, true);
    }

    private void admitHttp(ChannelHandlerContext ctx, Object msg) {
        if (this.capacityController.tryAcquire() == ExecutionCapacityController.Admission.SATURATED) {
            rejectHttp(ctx, msg);
            return;
        }
        dispatch(ctx, msg, false);
    }

    private void queuePacket(ChannelHandlerContext ctx, ClientMessage message) {
        if (this.pendingMessages.size() >= this.pendingCapacity) {
            message.release();
            this.metrics.recordOverflowDisconnect();
            endPause(false);
            ctx.close();
            return;
        }

        this.pendingMessages.addLast(message);
        if (this.paused) {
            return;
        }

        this.paused = true;
        this.pauseStartedAtNanos = System.nanoTime();
        this.autoReadWasEnabled = ctx.channel().config().isAutoRead();
        if (this.autoReadWasEnabled) {
            ctx.channel().config().setAutoRead(false);
        }
        if (!this.affectedRecorded) {
            this.affectedRecorded = true;
            this.metrics.recordAffectedConnection();
        }
        this.metrics.recordPauseStarted();
        this.pauseDeadline =
                ctx.executor().schedule(() -> onPauseTimeout(ctx), this.pauseTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    private void onPauseTimeout(ChannelHandlerContext ctx) {
        if (!this.paused) {
            return;
        }
        this.metrics.recordTimeoutDisconnect();
        endPause(false);
        ctx.close();
    }

    private void dispatch(ChannelHandlerContext ctx, Object msg, boolean packet) {
        ChannelHandlerContext target = ctx.pipeline().context(this.targetHandlerName);
        if (target == null) {
            releaseAdmission(ctx, packet);
            releaseMessage(msg);
            this.metrics.recordDispatchFailure();
            ctx.close();
            return;
        }

        Runnable task = () -> {
            try {
                ctx.fireChannelRead(msg);
            } finally {
                complete(ctx, packet);
            }
        };

        EventExecutor executor = target.executor();
        if (executor.inEventLoop()) {
            task.run();
            return;
        }

        try {
            executor.execute(task);
        } catch (RejectedExecutionException rejected) {
            releaseAdmission(ctx, packet);
            releaseMessage(msg);
            this.metrics.recordDispatchFailure();
            if (packet) {
                ctx.close();
            } else {
                writeBusyResponse(ctx);
            }
        }
    }

    private void complete(ChannelHandlerContext ctx, boolean packet) {
        if (!packet) {
            this.capacityController.release();
            return;
        }

        try {
            ctx.executor().execute(() -> {
                this.inFlight--;
                this.capacityController.release();
                drainOrResume(ctx);
            });
        } catch (RejectedExecutionException rejected) {
            this.capacityController.release();
        }
    }

    private void releaseAdmission(ChannelHandlerContext ctx, boolean packet) {
        if (packet && ctx.executor().inEventLoop()) {
            this.inFlight--;
        }
        this.capacityController.release();
    }

    private void scheduleDrain(ChannelHandlerContext ctx) {
        try {
            ctx.executor().execute(() -> drainOrResume(ctx));
        } catch (RejectedExecutionException ignored) {
            this.capacityController.cancelWaiter(this);
        }
    }

    private void drainOrResume(ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive()) {
            endPause(false);
            cleanupQueuedMessages();
            return;
        }

        if (!this.pendingMessages.isEmpty() && this.inFlight < this.perConnectionCapacity) {
            if (this.capacityController.tryAcquire(this) == ExecutionCapacityController.Admission.SATURATED) {
                this.capacityController.registerWaiter(this, () -> scheduleDrain(ctx));
                return;
            }
            ClientMessage next = this.pendingMessages.removeFirst();
            this.inFlight++;
            dispatch(ctx, next, true);
            if (!this.pendingMessages.isEmpty() && this.inFlight < this.perConnectionCapacity) {
                scheduleDrain(ctx);
            }
            return;
        }

        ExecutionCapacityController.Snapshot capacity = this.capacityController.snapshot();
        if (this.pendingMessages.isEmpty()
                && this.inFlight <= this.perConnectionLowWatermark
                && capacity.inFlight() <= capacity.lowWatermark()) {
            endPause(true);
            if (this.autoReadWasEnabled && ctx.channel().isActive()) {
                ctx.channel().config().setAutoRead(true);
            }
        } else if (this.paused && capacity.inFlight() > capacity.lowWatermark()) {
            this.capacityController.registerWaiter(this, () -> scheduleDrain(ctx));
        }
    }

    private void rejectHttp(ChannelHandlerContext ctx, Object msg) {
        releaseMessage(msg);
        this.metrics.recordHttpRejection();
        writeBusyResponse(ctx);
    }

    private static void writeBusyResponse(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE, Unpooled.wrappedBuffer(BUSY_RESPONSE));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, BUSY_RESPONSE.length);
        response.headers().set(HttpHeaderNames.RETRY_AFTER, "1");
        response.headers().set(HttpHeaderNames.CONNECTION, "close");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void endPause(boolean resumed) {
        if (!this.paused) {
            return;
        }
        this.paused = false;
        this.capacityController.cancelWaiter(this);
        if (this.pauseDeadline != null) {
            this.pauseDeadline.cancel(false);
            this.pauseDeadline = null;
        }
        this.metrics.recordPauseEnded(System.nanoTime() - this.pauseStartedAtNanos, resumed);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        endPause(false);
        cleanupQueuedMessages();
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        endPause(false);
        cleanupQueuedMessages();
    }

    private void cleanupQueuedMessages() {
        ClientMessage message;
        while ((message = this.pendingMessages.pollFirst()) != null) {
            message.release();
        }
    }

    private static void releaseMessage(Object message) {
        if (message instanceof ClientMessage clientMessage) {
            clientMessage.release();
        } else {
            ReferenceCountUtil.release(message);
        }
    }

    private enum Lane {
        GAME_PACKET {
            @Override
            boolean accepts(Object message) {
                return message instanceof ClientMessage;
            }
        },
        BLOCKING_HTTP {
            @Override
            boolean accepts(Object message) {
                return message instanceof FullHttpRequest;
            }
        };

        abstract boolean accepts(Object message);
    }
}
