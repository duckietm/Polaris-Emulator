package com.eu.habbo.networking.gameserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedExecutionBackpressureTest {
    private static final int MINIMUM_QUEUE_CAPACITY = 16;

    @Test
    void saturatedExecutorRejectsWithoutRunningWorkOnTheCaller() throws Exception {
        long rejectedBefore = ExecutionBackpressureMetrics.snapshot().packetRejectedTasks();
        AtomicBoolean ranOnCaller = new AtomicBoolean();
        EventExecutorGroup group = BoundedEventExecutorGroups.create(
                1, MINIMUM_QUEUE_CAPACITY, "PacketBackpressureTest", ExecutionBackpressureMetrics.Lane.GAME_PACKET);
        CountDownLatch releaseWorker = saturate(group);

        try {
            assertThrows(RejectedExecutionException.class, () -> group.execute(() -> ranOnCaller.set(true)));
            assertFalse(ranOnCaller.get(), "rejected work must never run on the Netty caller thread");
            assertEquals(
                    rejectedBefore + 1, ExecutionBackpressureMetrics.snapshot().packetRejectedTasks());
        } finally {
            releaseWorker.countDown();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    void saturatedHttpLaneReturnsRetryableServiceUnavailable() throws Exception {
        EventExecutorGroup group = BoundedEventExecutorGroups.create(
                1, MINIMUM_QUEUE_CAPACITY, "HttpBackpressureTest", ExecutionBackpressureMetrics.Lane.BLOCKING_HTTP);
        ExecutionCapacityGate capacityGate =
                new ExecutionCapacityGate(1, ExecutionBackpressureMetrics.Lane.BLOCKING_HTTP);
        assertTrue(capacityGate.tryAcquire());
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast("httpAdmission", ExecutionAdmissionHandler.forBlockingHttp("httpTarget", capacityGate))
                .addLast(group, "httpTarget", new ChannelInboundHandlerAdapter());
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/test");

        try {
            channel.writeInbound(request);
            FullHttpResponse response = channel.readOutbound();

            assertEquals(HttpResponseStatus.SERVICE_UNAVAILABLE, response.status());
            assertEquals("1", response.headers().get(HttpHeaderNames.RETRY_AFTER));
            assertEquals(0, request.refCnt(), "the rejected HTTP request must be released");
            response.release();
        } finally {
            capacityGate.release();
            channel.finishAndReleaseAll();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    void saturatedPacketLaneReleasesPayloadAndClosesChannel() throws Exception {
        EventExecutorGroup group = BoundedEventExecutorGroups.create(
                1, MINIMUM_QUEUE_CAPACITY, "PacketAdmissionTest", ExecutionBackpressureMetrics.Lane.GAME_PACKET);
        ExecutionCapacityGate capacityGate =
                new ExecutionCapacityGate(1, ExecutionBackpressureMetrics.Lane.GAME_PACKET);
        assertTrue(capacityGate.tryAcquire());
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast("packetAdmission", ExecutionAdmissionHandler.forGamePackets("packetTarget", capacityGate))
                .addLast(group, "packetTarget", new ChannelInboundHandlerAdapter());
        ByteBuf payload = Unpooled.buffer(1).writeByte(1);
        ClientMessage message = new ClientMessage(1, payload);

        try {
            channel.writeInbound(message);

            assertEquals(0, payload.refCnt(), "the rejected packet payload must be released");
            assertFalse(channel.isOpen(), "an overloaded game channel must be closed predictably");
        } finally {
            capacityGate.release();
            channel.finishAndReleaseAll();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    void completedWorkReturnsAdmissionCapacity() {
        EventExecutorGroup group = BoundedEventExecutorGroups.create(
                1, MINIMUM_QUEUE_CAPACITY, "AdmissionReleaseTest", ExecutionBackpressureMetrics.Lane.BLOCKING_HTTP);
        ExecutionCapacityGate capacityGate =
                new ExecutionCapacityGate(1, ExecutionBackpressureMetrics.Lane.BLOCKING_HTTP);
        AtomicInteger processed = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast("httpAdmission", ExecutionAdmissionHandler.forBlockingHttp("httpTarget", capacityGate))
                .addLast(group, "httpTarget", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) {
                        ReferenceCountUtil.release(msg);
                        processed.incrementAndGet();
                    }
                });

        try {
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first"));
            group.submit(() -> {}).syncUninterruptibly();
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second"));
            group.submit(() -> {}).syncUninterruptibly();

            assertEquals(2, processed.get());
            assertTrue(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    private static CountDownLatch saturate(EventExecutorGroup group) throws InterruptedException {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        group.execute(() -> {
            workerStarted.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        if (!workerStarted.await(2, TimeUnit.SECONDS)) {
            throw new AssertionError("worker did not start");
        }
        for (int index = 0; index < MINIMUM_QUEUE_CAPACITY; index++) {
            group.execute(() -> {});
        }
        return releaseWorker;
    }
}
