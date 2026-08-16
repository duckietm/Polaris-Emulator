package com.eu.habbo.networking.gameserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import com.eu.habbo.networking.gameserver.codec.WebSocketCodec;
import com.eu.habbo.networking.gameserver.decoders.GameByteDecoder;
import com.eu.habbo.networking.gameserver.decoders.GameByteFrameDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExecutionAdmissionNetworkIT {

    @Test
    void tcpConnectionPausesAndRecoversWithoutLosingPackets() throws Exception {
        runTransportScenario(false);
    }

    @Test
    void webSocketConnectionPausesAndRecoversWithoutLosingPackets() throws Exception {
        runTransportScenario(true);
    }

    private static void runTransportScenario(boolean webSocket) throws Exception {
        EventLoopGroup boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workers = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup clients = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        DefaultEventExecutorGroup packetWorkers = new DefaultEventExecutorGroup(1);
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics(webSocket ? "websocket" : "tcp");
        CountDownLatch accepted = new CountDownLatch(1);
        CountDownLatch handshake = new CountDownLatch(webSocket ? 1 : 0);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<Channel> serverConnection = new AtomicReference<>();
        List<Integer> handled = new CopyOnWriteArrayList<>();
        Channel listener = null;
        Channel client = null;
        try {
            ServerBootstrap server = new ServerBootstrap()
                    .group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            serverConnection.set(channel);
                            accepted.countDown();
                            if (webSocket) {
                                channel.pipeline().addLast(new HttpServerCodec());
                                channel.pipeline().addLast(new HttpObjectAggregator(16_384));
                                channel.pipeline().addLast(new WebSocketServerProtocolHandler("/"));
                                channel.pipeline().addLast(new WebSocketCodec());
                            }
                            channel.pipeline().addLast(new GameByteFrameDecoder());
                            channel.pipeline().addLast(new GameByteDecoder());
                            channel.pipeline()
                                    .addLast(
                                            "admission",
                                            ExecutionAdmissionHandler.forGamePackets(
                                                    "target", controller, metrics, 1, 0, 2, 2_000));
                            channel.pipeline()
                                    .addLast(
                                            packetWorkers,
                                            "target",
                                            new BlockingPacketHandler(firstStarted, releaseFirst, handled));
                        }
                    });
            listener = server.bind("127.0.0.1", 0).sync().channel();
            int port = ((InetSocketAddress) listener.localAddress()).getPort();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clients)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            if (!webSocket) {
                                return;
                            }
                            channel.pipeline().addLast(new HttpClientCodec());
                            channel.pipeline().addLast(new HttpObjectAggregator(16_384));
                            WebSocketClientProtocolConfig config = WebSocketClientProtocolConfig.newBuilder()
                                    .webSocketUri(URI.create("ws://127.0.0.1:" + port + "/"))
                                    .build();
                            channel.pipeline().addLast(new WebSocketClientProtocolHandler(config));
                            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object event)
                                        throws Exception {
                                    if (event
                                            == WebSocketClientProtocolHandler.ClientHandshakeStateEvent
                                                    .HANDSHAKE_COMPLETE) {
                                        handshake.countDown();
                                    }
                                    ctx.fireUserEventTriggered(event);
                                }
                            });
                        }
                    });
            client = clientBootstrap.connect("127.0.0.1", port).sync().channel();
            assertTrue(accepted.await(2, TimeUnit.SECONDS));
            assertTrue(handshake.await(2, TimeUnit.SECONDS));

            writePacket(client, webSocket, 1);
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            writePacket(client, webSocket, 2);

            Channel acceptedChannel = serverConnection.get();
            await(() -> !acceptedChannel.config().isAutoRead());
            assertTrue(acceptedChannel.isActive());
            assertFalse(acceptedChannel.config().isAutoRead());

            releaseFirst.countDown();
            await(() -> handled.size() == 2 && acceptedChannel.config().isAutoRead());

            assertEquals(List.of(1, 2), handled);
            assertTrue(acceptedChannel.isActive());
            assertEquals(1, metrics.snapshot().resumes());
        } finally {
            releaseFirst.countDown();
            if (client != null) {
                client.close().syncUninterruptibly();
            }
            if (listener != null) {
                listener.close().syncUninterruptibly();
            }
            packetWorkers.shutdownGracefully().syncUninterruptibly();
            clients.shutdownGracefully().syncUninterruptibly();
            workers.shutdownGracefully().syncUninterruptibly();
            boss.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static void writePacket(Channel channel, boolean webSocket, int messageId) {
        ByteBuf payload = channel.alloc().buffer(6).writeInt(2).writeShort(messageId);
        if (webSocket) {
            channel.writeAndFlush(new BinaryWebSocketFrame(payload)).syncUninterruptibly();
        } else {
            channel.writeAndFlush(payload).syncUninterruptibly();
        }
    }

    private static void await(Condition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.satisfied() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(condition.satisfied(), "condition was not satisfied before the deadline");
    }

    private interface Condition {
        boolean satisfied();
    }

    private static final class BlockingPacketHandler extends ChannelInboundHandlerAdapter {
        private final CountDownLatch firstStarted;
        private final CountDownLatch releaseFirst;
        private final List<Integer> handled;

        private BlockingPacketHandler(CountDownLatch firstStarted, CountDownLatch releaseFirst, List<Integer> handled) {
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
}
