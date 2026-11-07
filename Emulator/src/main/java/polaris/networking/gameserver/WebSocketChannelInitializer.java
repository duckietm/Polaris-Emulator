package polaris.networking.gameserver;

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
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.messages.PacketManager;
import polaris.networking.gameserver.auth.AuthHttpHandler;
import polaris.networking.gameserver.auth.NitroSecureApiHandler;
import polaris.networking.gameserver.auth.NitroSecureAssetHandler;
import polaris.networking.gameserver.badges.BadgeHttpHandler;
import polaris.networking.gameserver.badges.BadgeLeaderboardHttpHandler;
import polaris.networking.gameserver.battleball.BattleBallHttpHandler;
import polaris.networking.gameserver.cms.CmsApiHandler;
import polaris.networking.gameserver.codec.WebSocketCodec;
import polaris.networking.gameserver.crypto.WsHandshakeHandler;
import polaris.networking.gameserver.crypto.WsSessionCrypto;
import polaris.networking.gameserver.decoders.GameByteDecoder;
import polaris.networking.gameserver.decoders.GameByteFrameDecoder;
import polaris.networking.gameserver.decoders.GameClientMessageLogger;
import polaris.networking.gameserver.decoders.GameMessageHandler;
import polaris.networking.gameserver.decoders.GameMessageRateLimit;
import polaris.networking.gameserver.decoders.GamePolicyDecoder;
import polaris.networking.gameserver.decoders.PacketDispatchLatencyHandler;
import polaris.networking.gameserver.decoders.PacketDispatchMarker;
import polaris.networking.gameserver.encoders.GameServerMessageEncoder;
import polaris.networking.gameserver.encoders.GameServerMessageLogger;
import polaris.networking.gameserver.fastfood.FastFoodHttpHandler;
import polaris.networking.gameserver.hall.HallHttpHandler;
import polaris.networking.gameserver.handlers.IdleTimeoutHandler;
import polaris.networking.gameserver.handlers.PreUpgradeConnectionLimit;
import polaris.networking.gameserver.handlers.SustainedUnwritableHandler;
import polaris.networking.gameserver.handlers.WebSocketHttpCleanupHandler;
import polaris.networking.gameserver.handlers.WebSocketHttpHandler;
import polaris.networking.gameserver.ssl.SSLCertificateLoader;
import polaris.networking.gameserver.stats.EmuStatsHttpHandler;
import polaris.networking.gameserver.uno.UnoHttpHandler;
import polaris.networking.gameserver.werewolf.WerewolfHttpHandler;
import polaris.networking.gameserver.wired.WiredVariableApiHandler;
import polaris.networking.gameserver.wobble.WobbleHttpHandler;
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
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketChannelInitializer.class);
    private static final int MAX_FRAME_SIZE = 500000;
    // A connection that has not upgraded to WebSocket is closed after this many seconds without a read: before, an
    // HTTP connection could hold its aggregation buffer open for as long as it liked. 0 turns it off.
    static final String HTTP_READ_TIMEOUT_SETTING = "ws.http.read_timeout_sec";
    private static final int DEFAULT_HTTP_READ_TIMEOUT_SECONDS = 30;
    // Connections one client may hold before the upgrade; a trusted proxy is not counted. 0 turns it off.
    static final String HTTP_CONNECTIONS_SETTING = "ws.http.connections.per_ip";
    private static final int DEFAULT_HTTP_CONNECTIONS = 32;

    private final SslContext sslContext;
    private final boolean sslEnabled;
    private final WebSocketServerProtocolConfig wsConfig;
    private final int unwritableTimeoutSeconds;
    private final int blockingHttpThreads;

    public WebSocketChannelInitializer() {
        this(10, 8);
    }

    WebSocketChannelInitializer(int unwritableTimeoutSeconds) {
        this(unwritableTimeoutSeconds, 8);
    }

    WebSocketChannelInitializer(int unwritableTimeoutSeconds, int blockingHttpThreads) {
        if (unwritableTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("unwritableTimeoutSeconds must be positive");
        }
        this.unwritableTimeoutSeconds = unwritableTimeoutSeconds;
        this.blockingHttpThreads = blockingHttpThreads;
        this.sslContext = SSLCertificateLoader.getContext();
        this.sslEnabled = this.sslContext != null;
        this.wsConfig = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/")
                .checkStartsWith(true)
                .maxFramePayloadLength(MAX_FRAME_SIZE)
                // Offered only to a client that asks for it; see WsDirectionalSession.
                .subprotocols(WsSessionCrypto.SUBPROTOCOL_V2)
                .build();
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast("logger", new LoggingHandler());
        ch.pipeline()
                .addLast(
                        "outboundBackpressure",
                        new SustainedUnwritableHandler(this.unwritableTimeoutSeconds, TimeUnit.SECONDS));

        if (this.sslEnabled) {
            SSLEngine engine = this.sslContext.newEngine(ch.alloc());
            ch.pipeline().addLast(new SslHandler(engine));
        }

        ConfigurationManager configuration = Emulator.getConfig();
        int httpReadTimeout = configuration == null
                ? DEFAULT_HTTP_READ_TIMEOUT_SECONDS
                : configuration.getInt(HTTP_READ_TIMEOUT_SETTING, DEFAULT_HTTP_READ_TIMEOUT_SECONDS);
        if (httpReadTimeout > 0) {
            ch.pipeline().addLast("httpReadTimeout", new ReadTimeoutHandler(httpReadTimeout, TimeUnit.SECONDS));
        }
        int httpConnections = configuration == null
                ? DEFAULT_HTTP_CONNECTIONS
                : configuration.getInt(HTTP_CONNECTIONS_SETTING, DEFAULT_HTTP_CONNECTIONS);
        if (httpConnections > 0) {
            ch.pipeline().addLast("httpConnectionLimit", new PreUpgradeConnectionLimit(httpConnections));
        }

        ch.pipeline().addLast("httpCodec", new HttpServerCodec());
        ch.pipeline().addLast("httpAggregator", new HttpObjectAggregator(MAX_FRAME_SIZE));
        ch.pipeline().addLast("wsHttpHandler", new WebSocketHttpHandler());
        ExecutionBackpressureSettings backpressureSettings = configuration == null
                ? ExecutionBackpressureSettings.defaults()
                : ExecutionBackpressureSettings.from(configuration);
        EventExecutorGroup blockingHttp =
                BlockingHttpExecutionGroup.get(this.blockingHttpThreads, backpressureSettings);
        ch.pipeline()
                .addLast(
                        "blockingHttpAdmissionAssets",
                        BlockingHttpExecutionGroup.admissionHandler("nitroSecureAssetHandler"));
        ch.pipeline().addLast(blockingHttp, "nitroSecureAssetHandler", new NitroSecureAssetHandler());
        // The secure API derives a key for every request: on the socket event loop that stalled every other
        // connection of the loop, so it runs on the blocking HTTP executor like the asset handler.
        ch.pipeline()
                .addLast(
                        "blockingHttpAdmissionApi",
                        BlockingHttpExecutionGroup.admissionHandler("nitroSecureApiHandler"));
        ch.pipeline().addLast(blockingHttp, "nitroSecureApiHandler", new NitroSecureApiHandler());
        ch.pipeline().addLast("authHttpHandler", new AuthHttpHandler());
        ch.pipeline().addLast("blockingHttpAdmissionCms", BlockingHttpExecutionGroup.admissionHandler("cmsApiHandler"));
        ch.pipeline().addLast(blockingHttp, "cmsApiHandler", new CmsApiHandler());
        ch.pipeline()
                .addLast("blockingHttpAdmissionWired", BlockingHttpExecutionGroup.admissionHandler("wiredApiHandler"));
        ch.pipeline().addLast(blockingHttp, "wiredApiHandler", new WiredVariableApiHandler());
        ch.pipeline()
                .addLast("blockingHttpAdmissionBadge", BlockingHttpExecutionGroup.admissionHandler("badgeHttpHandler"));
        ch.pipeline().addLast(blockingHttp, "badgeHttpHandler", new BadgeHttpHandler());
        ch.pipeline()
                .addLast(
                        "blockingHttpAdmissionBadgeLeaderboard",
                        BlockingHttpExecutionGroup.admissionHandler("badgeLeaderboardHttpHandler"));
        ch.pipeline().addLast(blockingHttp, "badgeLeaderboardHttpHandler", new BadgeLeaderboardHttpHandler());
        ch.pipeline()
                .addLast(
        ch.pipeline()
                .addLast("blockingHttpAdmissionUno", BlockingHttpExecutionGroup.admissionHandler("unoHttpHandler"));
        ch.pipeline().addLast(blockingHttp, "unoHttpHandler", new UnoHttpHandler());
        ch.pipeline()
                .addLast(
                        "blockingHttpAdmissionWerewolf",
                        BlockingHttpExecutionGroup.admissionHandler("werewolfHttpHandler"));
        ch.pipeline().addLast(blockingHttp, "werewolfHttpHandler", new WerewolfHttpHandler());
        ch.pipeline()
                .addLast(
                        "blockingHttpAdmissionWobble",
                        BlockingHttpExecutionGroup.admissionHandler("wobbleHttpHandler"));
        ch.pipeline().addLast(blockingHttp, "wobbleHttpHandler", new WobbleHttpHandler());
        ch.pipeline()
                .addLast(
                        "blockingHttpAdmissionBattleBall",
                        BlockingHttpExecutionGroup.admissionHandler("battleBallHttpHandler"));
        ch.pipeline().addLast(blockingHttp, "battleBallHttpHandler", new BattleBallHttpHandler());
        ch.pipeline()
                .addLast(
                        "blockingHttpAdmissionFastFood",
                        BlockingHttpExecutionGroup.admissionHandler("fastFoodHttpHandler"));
        ch.pipeline().addLast(blockingHttp, "fastFoodHttpHandler", new FastFoodHttpHandler());
        ch.pipeline()
                .addLast("blockingHttpAdmissionHall", BlockingHttpExecutionGroup.admissionHandler("hallHttpHandler"));
        ch.pipeline().addLast(blockingHttp, "hallHttpHandler", new HallHttpHandler());
                        "blockingHttpAdmissionStats",
                        BlockingHttpExecutionGroup.admissionHandler("emuStatsHttpHandler"));
        ch.pipeline().addLast(blockingHttp, "emuStatsHttpHandler", new EmuStatsHttpHandler());
        ch.pipeline().addLast("wsProtocolHandler", new WebSocketServerProtocolHandler(this.wsConfig));
        ch.pipeline().addLast("wsHttpCleanup", new WebSocketHttpCleanupHandler());
        ch.pipeline().addLast("wsFrameAggregator", new WebSocketFrameAggregator(MAX_FRAME_SIZE));
        ch.pipeline().addLast("wsCodec", new WebSocketCodec());

        if (configuration != null && configuration.getBoolean("crypto.ws.enabled", false)) {
            ch.pipeline().addLast(WsHandshakeHandler.HANDLER_NAME, new WsHandshakeHandler());
        }

        ch.pipeline().addLast(new GamePolicyDecoder());
        ch.pipeline().addLast(new GameByteFrameDecoder());
        ch.pipeline().addLast(new GameByteDecoder());

        if (PacketManager.DEBUG_SHOW_PACKETS) {
            ch.pipeline().addLast(new GameClientMessageLogger());
        }

        ch.pipeline().addLast("idleEventHandler", new IdleTimeoutHandler(30, 60));
        ch.pipeline().addLast("gameClientRegistrar", new GameMessageHandler.ClientRegistrar());
        ch.pipeline().addLast(new GameMessageRateLimit());
        ch.pipeline().addLast("packetDispatchMarker", new PacketDispatchMarker());
        ch.pipeline()
                .addLast(
                        "packetExecutionAdmission", GamePacketExecutionGroup.admissionHandler("packetDispatchLatency"));
        ch.pipeline()
                .addLast(GamePacketExecutionGroup.get(), "packetDispatchLatency", new PacketDispatchLatencyHandler());
        ch.pipeline().addLast(GamePacketExecutionGroup.get(), "gameMessageHandler", new GameMessageHandler());
        ch.pipeline().addLast("messageEncoder", new GameServerMessageEncoder());

        if (PacketManager.DEBUG_SHOW_PACKETS) {
            ch.pipeline().addLast(new GameServerMessageLogger());
        }
    }

    public boolean isSslEnabled() {
        return this.sslEnabled;
    }
}
