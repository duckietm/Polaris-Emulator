package com.eu.habbo.networking.gameserver;

import com.eu.habbo.messages.ClientMessage;
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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;

final class ExecutionAdmissionHandler extends ChannelInboundHandlerAdapter {
    private static final byte[] BUSY_RESPONSE =
            "{\"error\":\"Server busy, try again shortly.\"}".getBytes(StandardCharsets.UTF_8);

    private final String targetHandlerName;
    private final Mode mode;
    private final ExecutionCapacityGate capacityGate;

    private ExecutionAdmissionHandler(String targetHandlerName, Mode mode, ExecutionCapacityGate capacityGate) {
        this.targetHandlerName = targetHandlerName;
        this.mode = mode;
        this.capacityGate = capacityGate;
    }

    static ExecutionAdmissionHandler forGamePackets(String targetHandlerName, ExecutionCapacityGate capacityGate) {
        return new ExecutionAdmissionHandler(targetHandlerName, Mode.GAME_PACKET, capacityGate);
    }

    static ExecutionAdmissionHandler forBlockingHttp(String targetHandlerName, ExecutionCapacityGate capacityGate) {
        return new ExecutionAdmissionHandler(targetHandlerName, Mode.BLOCKING_HTTP, capacityGate);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!this.mode.accepts(msg)) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (!this.capacityGate.tryAcquire()) {
            reject(ctx, msg);
            return;
        }

        ChannelHandlerContext target = ctx.pipeline().context(this.targetHandlerName);
        if (target == null) {
            this.capacityGate.release();
            reject(ctx, msg);
            return;
        }

        EventExecutor executor = target.executor();
        if (executor.inEventLoop()) {
            try {
                ctx.fireChannelRead(msg);
            } finally {
                this.capacityGate.release();
            }
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    ctx.fireChannelRead(msg);
                } finally {
                    this.capacityGate.release();
                }
            });
        } catch (RejectedExecutionException rejected) {
            this.capacityGate.release();
            reject(ctx, msg);
        }
    }

    private void reject(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ClientMessage clientMessage) {
            clientMessage.release();
            ctx.close();
            return;
        }

        ReferenceCountUtil.release(msg);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE, Unpooled.wrappedBuffer(BUSY_RESPONSE));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, BUSY_RESPONSE.length);
        response.headers().set(HttpHeaderNames.RETRY_AFTER, "1");
        response.headers().set(HttpHeaderNames.CONNECTION, "close");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private enum Mode {
        GAME_PACKET {
            @Override
            boolean accepts(Object msg) {
                return msg instanceof ClientMessage;
            }
        },
        BLOCKING_HTTP {
            @Override
            boolean accepts(Object msg) {
                return msg instanceof FullHttpRequest;
            }
        };

        abstract boolean accepts(Object msg);
    }
}
