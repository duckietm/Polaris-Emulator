package com.eu.habbo.networking.gameserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExecutionAdmissionHandlerTest {

    @Test
    void packetLanePausesAndResumesWithoutDroppingQueuedMessages() throws Exception {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("packet");
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        List<Integer> handled = new CopyOnWriteArrayList<>();
        DefaultEventExecutorGroup workers = new DefaultEventExecutorGroup(1);
        EmbeddedChannel channel =
                packetChannel(controller, metrics, 2_000, 2, workers, firstStarted, releaseFirst, handled);
        try {
            channel.writeInbound(message(1));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            channel.writeInbound(message(2));

            assertTrue(channel.isActive());
            assertFalse(channel.config().isAutoRead());
            assertEquals(1, metrics.snapshot().activePauses());

            releaseFirst.countDown();
            await(channel, () -> handled.size() == 2 && channel.config().isAutoRead());

            assertEquals(List.of(1, 2), handled);
            assertTrue(channel.isActive());
            assertTrue(channel.config().isAutoRead());
            assertEquals(0, metrics.snapshot().activePauses());
            assertEquals(1, metrics.snapshot().resumes());
        } finally {
            releaseFirst.countDown();
            channel.finishAndReleaseAll();
            workers.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void sustainedPacketCongestionDisconnectsAfterTheConfiguredDeadline() throws Exception {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("packet");
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        DefaultEventExecutorGroup workers = new DefaultEventExecutorGroup(1);
        EmbeddedChannel channel = packetChannel(
                controller, metrics, 40, 2, workers, firstStarted, releaseFirst, new CopyOnWriteArrayList<>());
        try {
            channel.writeInbound(message(1));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            channel.writeInbound(message(2));

            Thread.sleep(60);
            channel.runScheduledPendingTasks();
            channel.runPendingTasks();

            assertFalse(channel.isActive());
            assertEquals(1, metrics.snapshot().timeoutDisconnects());
        } finally {
            releaseFirst.countDown();
            channel.finishAndReleaseAll();
            workers.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void exceedingThePerConnectionPendingLimitClosesAndReleasesMessages() throws Exception {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("packet");
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        DefaultEventExecutorGroup workers = new DefaultEventExecutorGroup(1);
        EmbeddedChannel channel = packetChannel(
                controller, metrics, 2_000, 1, workers, firstStarted, releaseFirst, new CopyOnWriteArrayList<>());
        ClientMessage queued = message(2);
        ClientMessage overflow = message(3);
        try {
            channel.writeInbound(message(1));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            channel.writeInbound(queued);
            channel.writeInbound(overflow);
            channel.runPendingTasks();

            assertFalse(channel.isActive());
            assertEquals(0, queued.getBuffer().refCnt());
            assertEquals(0, overflow.getBuffer().refCnt());
            assertEquals(1, metrics.snapshot().overflowDisconnects());
        } finally {
            releaseFirst.countDown();
            channel.finishAndReleaseAll();
            workers.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void closingAPausedConnectionReleasesItsPendingMessagesAndWaiter() throws Exception {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("packet");
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        DefaultEventExecutorGroup workers = new DefaultEventExecutorGroup(1);
        EmbeddedChannel channel = packetChannel(
                controller, metrics, 2_000, 2, workers, firstStarted, releaseFirst, new CopyOnWriteArrayList<>());
        ClientMessage queued = message(2);
        try {
            channel.writeInbound(message(1));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            channel.writeInbound(queued);

            channel.close().syncUninterruptibly();
            channel.runPendingTasks();

            assertEquals(0, queued.getBuffer().refCnt());
            assertEquals(0, metrics.snapshot().activePauses());
            assertEquals(0, controller.snapshot().waitingConnections());
        } finally {
            releaseFirst.countDown();
            channel.finishAndReleaseAll();
            workers.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void missingDispatchTargetClosesAndReleasesTheAdmission() {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("packet");
        EmbeddedChannel channel = new EmbeddedChannel(
                ExecutionAdmissionHandler.forGamePackets("missing", controller, metrics, 1, 0, 1, 2_000));
        ClientMessage message = message(1);
        try {
            channel.writeInbound(message);
            channel.runPendingTasks();

            assertFalse(channel.isActive());
            assertEquals(0, message.getBuffer().refCnt());
            assertEquals(0, controller.snapshot().inFlight());
            assertEquals(1, metrics.snapshot().dispatchFailures());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void observeModeRecordsPressureWithoutPausingTheConnection() throws Exception {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.OBSERVE);
        ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("packet");
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        List<Integer> handled = new CopyOnWriteArrayList<>();
        DefaultEventExecutorGroup workers = new DefaultEventExecutorGroup(1);
        EmbeddedChannel channel =
                packetChannel(controller, metrics, 2_000, 1, workers, firstStarted, releaseFirst, handled);
        try {
            channel.writeInbound(message(1));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            channel.writeInbound(message(2));

            assertTrue(channel.isActive());
            assertTrue(channel.config().isAutoRead());
            assertEquals(1, controller.snapshot().wouldThrottle());

            releaseFirst.countDown();
            await(channel, () -> handled.size() == 2);
            assertEquals(List.of(1, 2), handled);
        } finally {
            releaseFirst.countDown();
            channel.finishAndReleaseAll();
            workers.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void sharedCapacityServesAnOlderWaitingConnectionBeforeTheReleasingConnection() throws Exception {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("packet");
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        List<Integer> handled = new CopyOnWriteArrayList<>();
        DefaultEventExecutorGroup workers = new DefaultEventExecutorGroup(2);
        EmbeddedChannel first =
                packetChannel(controller, metrics, 2_000, 2, workers, firstStarted, releaseFirst, handled);
        EmbeddedChannel second = packetChannel(
                controller, metrics, 2_000, 2, workers, new CountDownLatch(1), new CountDownLatch(0), handled);
        try {
            first.writeInbound(message(1));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            second.writeInbound(message(100));
            first.writeInbound(message(2));

            releaseFirst.countDown();
            awaitBoth(first, second, () -> handled.size() == 3);

            assertEquals(List.of(1, 100, 2), handled);
        } finally {
            releaseFirst.countDown();
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
            workers.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void saturatedHttpLaneReturnsRetryableServiceUnavailable() throws Exception {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("http");
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        DefaultEventExecutorGroup workers = new DefaultEventExecutorGroup(1);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast("admission", ExecutionAdmissionHandler.forBlockingHttp("target", controller, metrics));
        channel.pipeline().addLast(workers, "target", new BlockingHandler(firstStarted, releaseFirst));
        try {
            channel.writeInbound(request("/first"));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            DefaultFullHttpRequest rejected = request("/second");
            channel.writeInbound(rejected);
            channel.runPendingTasks();

            FullHttpResponse response = channel.readOutbound();
            assertEquals(HttpResponseStatus.SERVICE_UNAVAILABLE, response.status());
            assertEquals("1", response.headers().get(HttpHeaderNames.RETRY_AFTER));
            assertEquals(0, rejected.refCnt());
            assertEquals(1, metrics.snapshot().httpRejections());
            ReferenceCountUtil.release(response);
        } finally {
            releaseFirst.countDown();
            channel.finishAndReleaseAll();
            workers.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static EmbeddedChannel packetChannel(
            ExecutionCapacityController controller,
            ExecutionBackpressureMetrics metrics,
            long timeoutMillis,
            int pendingCapacity,
            DefaultEventExecutorGroup workers,
            CountDownLatch firstStarted,
            CountDownLatch releaseFirst,
            List<Integer> handled) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(
                        "admission",
                        ExecutionAdmissionHandler.forGamePackets(
                                "target", controller, metrics, 1, 0, pendingCapacity, timeoutMillis));
        channel.pipeline().addLast(workers, "target", new PacketHandler(firstStarted, releaseFirst, handled));
        return channel;
    }

    private static ClientMessage message(int id) {
        return new ClientMessage(id, Unpooled.buffer(1).writeByte(id));
    }

    private static DefaultFullHttpRequest request(String path) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
    }

    private static void await(EmbeddedChannel channel, Condition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.satisfied() && System.nanoTime() < deadline) {
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
            Thread.sleep(5);
        }
        assertTrue(condition.satisfied(), "condition was not satisfied before the deadline");
    }

    private static void awaitBoth(EmbeddedChannel first, EmbeddedChannel second, Condition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.satisfied() && System.nanoTime() < deadline) {
            first.runPendingTasks();
            first.runScheduledPendingTasks();
            second.runPendingTasks();
            second.runScheduledPendingTasks();
            Thread.sleep(5);
        }
        assertTrue(condition.satisfied(), "condition was not satisfied before the deadline");
    }

    private interface Condition {
        boolean satisfied();
    }

    private static final class PacketHandler extends ChannelInboundHandlerAdapter {
        private final CountDownLatch firstStarted;
        private final CountDownLatch releaseFirst;
        private final List<Integer> handled;

        private PacketHandler(CountDownLatch firstStarted, CountDownLatch releaseFirst, List<Integer> handled) {
            this.firstStarted = firstStarted;
            this.releaseFirst = releaseFirst;
            this.handled = handled;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ClientMessage message = (ClientMessage) msg;
            try {
                if (this.handled.isEmpty()) {
                    this.firstStarted.countDown();
                    this.releaseFirst.await(2, TimeUnit.SECONDS);
                }
                this.handled.add(message.getMessageId());
            } finally {
                message.release();
            }
        }
    }

    private static final class BlockingHandler extends ChannelInboundHandlerAdapter {
        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingHandler(CountDownLatch started, CountDownLatch release) {
            this.started = started;
            this.release = release;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                this.started.countDown();
                this.release.await(2, TimeUnit.SECONDS);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }
}
