package com.eu.habbo.networking;

import com.eu.habbo.Emulator;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);
    private static final int DEFAULT_WRITE_BUFFER_LOW_WATER_MARK = 32 * 1024;
    private static final int DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK = 64 * 1024;
    private static final int DEFAULT_UNWRITABLE_TIMEOUT_SECONDS = 10;

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
                            && "true"
                                    .equalsIgnoreCase(
                                            Emulator.getConfig().getValue("io.netty.allocator.pooled", "false"));
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
    private volatile boolean listening;
    private volatile Channel serverChannel;

    public Server(String name, String host, int port, int bossGroupThreads, int workerGroupThreads) throws Exception {
        this.name = name;
        this.host = host;
        this.port = port;

        String threadName = name.replace("Server", "").replace(" ", "");

        // Netty 4.2: NioEventLoopGroup is deprecated in favour of the generic
        // MultiThreadIoEventLoopGroup driven by an IoHandlerFactory (NIO here).
        this.bossGroup = new MultiThreadIoEventLoopGroup(
                bossGroupThreads, new DefaultThreadFactory(threadName + "Boss"), NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(
                workerGroupThreads, new DefaultThreadFactory(threadName + "Worker"), NioIoHandler.newFactory());
        this.serverBootstrap = new ServerBootstrap();
    }

    public void initializePipeline() {
        this.serverBootstrap.group(this.bossGroup, this.workerGroup);
        this.serverBootstrap.channel(NioServerSocketChannel.class);
        configureTransportOptions(this.serverBootstrap);
    }

    protected static void configureTransportOptions(ServerBootstrap bootstrap) {
        bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.childOption(ChannelOption.SO_REUSEADDR, true);
        bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(1024, 8192, 65536));
        bootstrap.childOption(ChannelOption.ALLOCATOR, allocator());
        bootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, configuredWriteBufferWaterMark());
    }

    protected static WriteBufferWaterMark configuredWriteBufferWaterMark() {
        int low = DEFAULT_WRITE_BUFFER_LOW_WATER_MARK;
        int high = DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK;
        if (Emulator.getConfig() != null) {
            low = Emulator.getConfig().getInt("io.netty.write_buffer.low_water_mark", low);
            high = Emulator.getConfig().getInt("io.netty.write_buffer.high_water_mark", high);
        }

        if (low < 0 || high <= low) {
            LOGGER.warn(
                    "Invalid Netty write-buffer water marks low={} high={}; using defaults low={} high={}",
                    low,
                    high,
                    DEFAULT_WRITE_BUFFER_LOW_WATER_MARK,
                    DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK);
            low = DEFAULT_WRITE_BUFFER_LOW_WATER_MARK;
            high = DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK;
        }
        return new WriteBufferWaterMark(low, high);
    }

    protected static int configuredUnwritableTimeoutSeconds() {
        int timeout = Emulator.getConfig() == null
                ? DEFAULT_UNWRITABLE_TIMEOUT_SECONDS
                : Emulator.getConfig()
                        .getInt("io.netty.unwritable.timeout.seconds", DEFAULT_UNWRITABLE_TIMEOUT_SECONDS);
        if (timeout <= 0) {
            LOGGER.warn(
                    "Invalid Netty unwritable timeout {}; using default {} seconds",
                    timeout,
                    DEFAULT_UNWRITABLE_TIMEOUT_SECONDS);
            return DEFAULT_UNWRITABLE_TIMEOUT_SECONDS;
        }
        return timeout;
    }

    public void connect() {
        ChannelFuture channelFuture = this.bind(this.serverBootstrap, this.host, this.port);
        channelFuture.awaitUninterruptibly();

        if (!channelFuture.isSuccess()) {
            this.listening = false;
            throw new ServerBindException(this.name, this.host, this.port, channelFuture.cause());
        } else {
            this.serverChannel = channelFuture.channel();
            this.listening = true;
            channelFuture.channel().closeFuture().addListener(ignored -> this.listening = false);
            LOGGER.info("Started {} on {}:{}", this.name, this.host, this.port);
        }
    }

    protected ChannelFuture bind(ServerBootstrap bootstrap, String host, int port) {
        return bootstrap.bind(host, port);
    }

    public void stop() {
        this.listening = false;
        LOGGER.info("Stopping {}", this.name);
        if (this.serverChannel != null) {
            this.serverChannel.close().syncUninterruptibly();
        }
        try {
            this.workerGroup
                    .shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS)
                    .sync();
            this.bossGroup.shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS).sync();
        } catch (InterruptedException e) {
            LOGGER.error("Exception during {} shutdown... HARD STOP", this.name, e);
            Thread.currentThread().interrupt();
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

    public boolean isListening() {
        return this.listening && this.serverChannel != null && this.serverChannel.isActive();
    }
}
