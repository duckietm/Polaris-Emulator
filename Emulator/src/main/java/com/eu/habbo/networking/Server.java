package com.eu.habbo.networking;

import com.eu.habbo.Emulator;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public abstract class Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    private static volatile ByteBufAllocator sharedAllocator;

    /**
     * Shared channel allocator. Defaults to unpooled-heap (the long-standing
     * behaviour); set {@code io.netty.allocator.pooled=true} to switch to a
     * pooled HEAP allocator (preferDirect=false, so the array-backed crypto
     * paths keep working) which removes the per-packet alloc/GC churn. Opt-in
     * until validated under load with the Netty leak detector, since pooled
     * buffers that aren't released accumulate instead of being GC-reclaimed.
     */
    protected static ByteBufAllocator allocator() {
        if (sharedAllocator == null) {
            synchronized (Server.class) {
                if (sharedAllocator == null) {
                    boolean pooled = Emulator.getConfig() != null
                            && "true".equalsIgnoreCase(Emulator.getConfig().getValue("io.netty.allocator.pooled", "false"));
                    sharedAllocator = pooled ? new PooledByteBufAllocator(false) : new UnpooledByteBufAllocator(false);
                    LOGGER.info("Netty ByteBuf allocator: {}", pooled ? "pooled-heap" : "unpooled-heap");
                }
            }
        }
        return sharedAllocator;
    }

    protected final ServerBootstrap serverBootstrap;
    protected final EventLoopGroup bossGroup;
    protected final EventLoopGroup workerGroup;
    private final String name;
    private final String host;
    private final int port;

    public Server(String name, String host, int port, int bossGroupThreads, int workerGroupThreads) throws Exception {
        this.name = name;
        this.host = host;
        this.port = port;

        String threadName = name.replace("Server", "").replace(" ", "");

        // Netty 4.2: NioEventLoopGroup is deprecated in favour of the generic
        // MultiThreadIoEventLoopGroup driven by an IoHandlerFactory (NIO here).
        this.bossGroup = new MultiThreadIoEventLoopGroup(bossGroupThreads, new DefaultThreadFactory(threadName + "Boss"), NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(workerGroupThreads, new DefaultThreadFactory(threadName + "Worker"), NioIoHandler.newFactory());
        this.serverBootstrap = new ServerBootstrap();
    }

    public void initializePipeline() {
        this.serverBootstrap.group(this.bossGroup, this.workerGroup);
        this.serverBootstrap.channel(NioServerSocketChannel.class);
        this.serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        this.serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        this.serverBootstrap.childOption(ChannelOption.SO_REUSEADDR, true);
        this.serverBootstrap.childOption(ChannelOption.SO_RCVBUF, 4096);
        this.serverBootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(4096));
        this.serverBootstrap.childOption(ChannelOption.ALLOCATOR, allocator());
    }

    public void connect() {
        ChannelFuture channelFuture = this.serverBootstrap.bind(this.host, this.port);

        while (!channelFuture.isDone()) {
        }

        if (!channelFuture.isSuccess()) {
            LOGGER.info("Failed to start {} on {}:{}", this.name, this.host, this.port);
            System.exit(0);
        } else {
            LOGGER.info("Started {} on {}:{}", this.name, this.host, this.port);
        }
    }

    public void stop() {
        LOGGER.info("Stopping {}", this.name);
        try {
            this.workerGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
            this.bossGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        } catch(InterruptedException e) {
            LOGGER.error("Exception during {} shutdown... HARD STOP", this.name, e);
        }
        LOGGER.info("Stopped {}", this.name);
    }

    public ServerBootstrap getServerBootstrap() {
        return this.serverBootstrap;
    }

    public EventLoopGroup getBossGroup() {
        return this.bossGroup;
    }

    public EventLoopGroup getWorkerGroup() {
        return this.workerGroup;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }
}
