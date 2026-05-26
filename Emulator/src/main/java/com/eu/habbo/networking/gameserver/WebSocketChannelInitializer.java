package com.eu.habbo.networking.gameserver;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.PacketManager;
import com.eu.habbo.networking.gameserver.auth.AuthHttpHandler;
import com.eu.habbo.networking.gameserver.auth.NitroSecureApiHandler;
import com.eu.habbo.networking.gameserver.auth.NitroSecureAssetHandler;
import com.eu.habbo.networking.gameserver.badges.BadgeLeaderboardHttpHandler;
import com.eu.habbo.networking.gameserver.badges.BadgeHttpHandler;
import com.eu.habbo.networking.gameserver.codec.WebSocketCodec;
import com.eu.habbo.networking.gameserver.crypto.WsHandshakeHandler;
import com.eu.habbo.networking.gameserver.decoders.*;
import com.eu.habbo.networking.gameserver.encoders.GameServerMessageEncoder;
import com.eu.habbo.networking.gameserver.encoders.GameServerMessageLogger;
import com.eu.habbo.networking.gameserver.handlers.IdleTimeoutHandler;
import com.eu.habbo.networking.gameserver.handlers.WebSocketHttpHandler;
import com.eu.habbo.networking.gameserver.stats.EmuStatsHttpHandler;
import com.eu.habbo.networking.gameserver.ssl.SSLCertificateLoader;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {
    private static final int MAX_FRAME_SIZE = 500000;

    private final SslContext sslContext;
    private final boolean sslEnabled;
    private final WebSocketServerProtocolConfig wsConfig;

    public WebSocketChannelInitializer() {
        this.sslContext = SSLCertificateLoader.getContext();
        this.sslEnabled = this.sslContext != null;
        this.wsConfig = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/")
                .checkStartsWith(true)
                .maxFramePayloadLength(MAX_FRAME_SIZE)
                .build();
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast("logger", new LoggingHandler());

        if (this.sslEnabled) {
            SSLEngine engine = this.sslContext.newEngine(ch.alloc());
            ch.pipeline().addLast(new SslHandler(engine));
        }

        ch.pipeline().addLast("httpCodec", new HttpServerCodec());
        ch.pipeline().addLast("httpAggregator", new HttpObjectAggregator(MAX_FRAME_SIZE));
        ch.pipeline().addLast("wsHttpHandler", new WebSocketHttpHandler());
        ch.pipeline().addLast("nitroSecureAssetHandler", new NitroSecureAssetHandler());
        ch.pipeline().addLast("nitroSecureApiHandler", new NitroSecureApiHandler());
        ch.pipeline().addLast("authHttpHandler", new AuthHttpHandler());
        ch.pipeline().addLast("badgeHttpHandler", new BadgeHttpHandler());
        ch.pipeline().addLast("badgeLeaderboardHttpHandler", new BadgeLeaderboardHttpHandler());
        ch.pipeline().addLast("emuStatsHttpHandler", new EmuStatsHttpHandler());
        ch.pipeline().addLast("wsProtocolHandler", new WebSocketServerProtocolHandler(this.wsConfig));
        ch.pipeline().addLast("wsFrameAggregator", new WebSocketFrameAggregator(MAX_FRAME_SIZE));
        ch.pipeline().addLast("wsCodec", new WebSocketCodec());

        if (Emulator.getConfig().getBoolean("crypto.ws.enabled", false)) {
            ch.pipeline().addLast(WsHandshakeHandler.HANDLER_NAME, new WsHandshakeHandler());
        }

        ch.pipeline().addLast(new GamePolicyDecoder());
        ch.pipeline().addLast(new GameByteFrameDecoder());
        ch.pipeline().addLast(new GameByteDecoder());

        if (PacketManager.DEBUG_SHOW_PACKETS) {
            ch.pipeline().addLast(new GameClientMessageLogger());
        }

        ch.pipeline().addLast("idleEventHandler", new IdleTimeoutHandler(30, 60));
        ch.pipeline().addLast(new GameMessageRateLimit());
        ch.pipeline().addLast(new GameMessageHandler());
        ch.pipeline().addLast("messageEncoder", new GameServerMessageEncoder());

        if (PacketManager.DEBUG_SHOW_PACKETS) {
            ch.pipeline().addLast(new GameServerMessageLogger());
        }
    }

    public boolean isSslEnabled() {
        return this.sslEnabled;
    }
}
