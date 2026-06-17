package com.eu.habbo.networking.gameserver;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClientManager;
import com.eu.habbo.messages.PacketManager;
import com.eu.habbo.networking.Server;
import com.eu.habbo.networking.gameserver.decoders.*;
import com.eu.habbo.networking.gameserver.encoders.GameServerMessageEncoder;
import com.eu.habbo.networking.gameserver.encoders.GameServerMessageLogger;
import com.eu.habbo.networking.gameserver.handlers.IdleTimeoutHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameServer extends Server {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameServer.class);

    private final PacketManager packetManager;
    private final GameClientManager gameClientManager;
    private ServerBootstrap webSocketBootstrap;

    public GameServer(String host, int port) throws Exception {
        super("Game Server", host, port, Emulator.getConfig().getInt("io.bossgroup.threads"), Emulator.getConfig().getInt("io.workergroup.threads"));
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
                ch.pipeline().addLast(new GameMessageHandler());

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
        if (!Emulator.getConfig().getBoolean("ws.enabled", false)) {
            return;
        }

        String wsHost = Emulator.getConfig().getValue("ws.host", "0.0.0.0");
        int wsPort = Emulator.getConfig().getInt("ws.port", 2096);

        WebSocketChannelInitializer wsInitializer = new WebSocketChannelInitializer();

        this.webSocketBootstrap = new ServerBootstrap();
        this.webSocketBootstrap.group(this.getBossGroup(), this.getWorkerGroup());
        this.webSocketBootstrap.channel(NioServerSocketChannel.class);
        this.webSocketBootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        this.webSocketBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        this.webSocketBootstrap.childOption(ChannelOption.SO_REUSEADDR, true);
        this.webSocketBootstrap.childOption(ChannelOption.SO_RCVBUF, 4096);
        this.webSocketBootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(4096));
        this.webSocketBootstrap.childOption(ChannelOption.ALLOCATOR, allocator());
        this.webSocketBootstrap.childHandler(wsInitializer);

        ChannelFuture wsFuture = this.webSocketBootstrap.bind(wsHost, wsPort);

        while (!wsFuture.isDone()) {
        }

        if (!wsFuture.isSuccess()) {
            LOGGER.error("Failed to start WebSocket server on {}:{}", wsHost, wsPort);
        } else {
            LOGGER.info("WebSocket server started on {}:{} (SSL: {})", wsHost, wsPort, wsInitializer.isSslEnabled());

            if (com.eu.habbo.Emulator.getConfig().getBoolean("crypto.ws.signing.enabled", false)) {
                try {
                    com.eu.habbo.networking.gameserver.crypto.CryptoSigningKeyManager.get();
                    LOGGER.info("[ws-crypto] signing public key ready: {}",
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
}
