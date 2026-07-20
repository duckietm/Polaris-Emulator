package com.eu.habbo.networking.gameserver;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.gameclients.GameClientManager;
import com.eu.habbo.messages.PacketManager;
import com.eu.habbo.networking.Server;
import com.eu.habbo.networking.ServerBindException;
import com.eu.habbo.networking.gameserver.decoders.GameByteDecoder;
import com.eu.habbo.networking.gameserver.decoders.GameByteFrameDecoder;
import com.eu.habbo.networking.gameserver.decoders.GameClientMessageLogger;
import com.eu.habbo.networking.gameserver.decoders.GameMessageHandler;
import com.eu.habbo.networking.gameserver.decoders.GameMessageRateLimit;
import com.eu.habbo.networking.gameserver.decoders.GamePolicyDecoder;
import com.eu.habbo.networking.gameserver.decoders.PacketDispatchLatencyHandler;
import com.eu.habbo.networking.gameserver.decoders.PacketDispatchMarker;
import com.eu.habbo.networking.gameserver.encoders.GameServerMessageEncoder;
import com.eu.habbo.networking.gameserver.encoders.GameServerMessageLogger;
import com.eu.habbo.networking.gameserver.handlers.IdleTimeoutHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameServer extends Server {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameServer.class);

    private final PacketManager packetManager;
    private final GameClientManager gameClientManager;
    private ServerBootstrap webSocketBootstrap;
    private volatile boolean webSocketListening;
    private volatile Channel webSocketChannel;

    public GameServer(String host, int port) throws Exception {
        super(
                "Game Server",
                host,
                port,
                Emulator.getConfig().getInt("io.bossgroup.threads"),
                Emulator.getConfig().getInt("io.workergroup.threads"));
        this.packetManager = new PacketManager();
        this.gameClientManager = new GameClientManager();
    }

    @Override
    public void initializePipeline() {
        super.initializePipeline();

        this.serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast("logger", new LoggingHandler());

                // Decoders.
                ch.pipeline().addLast(new GamePolicyDecoder());
                ch.pipeline().addLast(new GameByteFrameDecoder());
                ch.pipeline().addLast(new GameByteDecoder());

                if (PacketManager.DEBUG_SHOW_PACKETS) {
                    ch.pipeline().addLast(new GameClientMessageLogger());
                }
                ch.pipeline().addLast("idleEventHandler", new IdleTimeoutHandler(30, 60));
                ch.pipeline().addLast(new GameMessageRateLimit());
                ch.pipeline().addLast("packetDispatchMarker", new PacketDispatchMarker());
                ch.pipeline()
                        .addLast(
                                GamePacketExecutionGroup.get(),
                                "packetDispatchLatency",
                                new PacketDispatchLatencyHandler());
                ch.pipeline().addLast(GamePacketExecutionGroup.get(), "gameMessageHandler", new GameMessageHandler());

                // Encoders.
                ch.pipeline().addLast(new GameServerMessageEncoder());

                if (PacketManager.DEBUG_SHOW_PACKETS) {
                    ch.pipeline().addLast(new GameServerMessageLogger());
                }
            }
        });

        initializeWebSocketServer();
    }

    private void initializeWebSocketServer() {
        this.webSocketListening = false;
        if (!Emulator.getConfig().getBoolean("ws.enabled", false)) {
            return;
        }

        String wsHost = Emulator.getConfig().getValue("ws.host", "0.0.0.0");
        int wsPort = Emulator.getConfig().getInt("ws.port", 2096);

        WebSocketChannelInitializer wsInitializer = new WebSocketChannelInitializer();

        this.webSocketBootstrap = new ServerBootstrap();
        this.webSocketBootstrap.group(this.getBossGroup(), this.getWorkerGroup());
        this.webSocketBootstrap.channel(NioServerSocketChannel.class);
        configureTransportOptions(this.webSocketBootstrap);
        this.webSocketBootstrap.childHandler(wsInitializer);

        ChannelFuture wsFuture = this.bind(this.webSocketBootstrap, wsHost, wsPort);
        wsFuture.awaitUninterruptibly();

        if (!wsFuture.isSuccess()) {
            throw new ServerBindException("WebSocket Server", wsHost, wsPort, wsFuture.cause());
        } else {
            this.webSocketChannel = wsFuture.channel();
            this.webSocketListening = true;
            wsFuture.channel().closeFuture().addListener(ignored -> this.webSocketListening = false);
            LOGGER.info("WebSocket server started on {}:{} (SSL: {})", wsHost, wsPort, wsInitializer.isSslEnabled());

            if (com.eu.habbo.Emulator.getConfig().getBoolean("crypto.ws.signing.enabled", false)) {
                try {
                    com.eu.habbo.networking.gameserver.crypto.CryptoSigningKeyManager.get();
                    LOGGER.info(
                            "[ws-crypto] signing public key ready: {}",
                            com.eu.habbo.networking.gameserver.crypto.CryptoSigningKeyManager.publicKeyBase64());
                } catch (Exception e) {
                    LOGGER.error("[ws-crypto] failed to warm signing keypair", e);
                }
            }
        }
    }

    public PacketManager getPacketManager() {
        return this.packetManager;
    }

    public GameClientManager getGameClientManager() {
        return this.gameClientManager;
    }

    public boolean isWebSocketListening() {
        return this.webSocketListening && this.webSocketChannel != null && this.webSocketChannel.isActive();
    }

    @Override
    public void stop() {
        this.webSocketListening = false;
        if (this.webSocketChannel != null) {
            this.webSocketChannel.close().syncUninterruptibly();
        }
        for (GameClient client :
                new ArrayList<>(this.gameClientManager.getSessions().values())) {
            this.gameClientManager.forceDisposeClient(client);
        }

        GamePacketExecutionGroup.shutdown();

        super.stop();
    }
}
