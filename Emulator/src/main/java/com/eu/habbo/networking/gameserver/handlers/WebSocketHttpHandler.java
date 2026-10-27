package com.eu.habbo.networking.gameserver.handlers;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import com.eu.habbo.networking.gameserver.auth.AuthHttpUtil;
import com.eu.habbo.networking.gameserver.auth.GameConnectionLimits;
import com.eu.habbo.networking.gameserver.e2e.E2eSessionProbe;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketHttpHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketHttpHandler.class);
    private static final String ORIGIN_HEADER = "Origin";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean upgrade = false;
        if (msg instanceof HttpMessage request) {
            if (!handleHttpRequest(ctx, request)) {
                ReferenceCountUtil.release(msg);
                return;
            }
            upgrade = isWebSocketUpgrade(request);
        }
        super.channelRead(ctx, msg);

        // Only an accepted upgrade ends the HTTP phase. Leaving after the first request of any kind let
        // the next keep-alive request skip the Origin check and keep the forwarded IP of the first one.
        if (upgrade) {
            ctx.pipeline().remove(this);
        }
    }

    private boolean handleHttpRequest(ChannelHandlerContext ctx, HttpMessage req) {
        ConfigurationManager config = Emulator.getConfig();
        if (req instanceof FullHttpRequest fullRequest
                && E2eSessionProbe.tryHandle(
                        fullRequest,
                        ctx,
                        Emulator.getGameServer().getGameClientManager(),
                        config.getBoolean("e2e.enabled", false))) {
            return false;
        }

        captureForwardedIp(ctx, req, config);

        if (!isWebSocketUpgrade(req)) {
            return true;
        }

        String origin = "error";

        try {
            if (req.headers().contains(ORIGIN_HEADER)) {
                origin = getDomainNameFromUrl(req.headers().get(ORIGIN_HEADER));
            }
        } catch (Exception ignored) {
        }

        String whitelist = config.getValue("ws.whitelist", "localhost");
        if (!isWhitelisted(origin, whitelist.split(","))) {
            LOGGER.warn(
                    "WebSocket upgrade rejected — origin '{}' not in ws.whitelist='{}'",
                    req.headers().get(ORIGIN_HEADER),
                    whitelist);

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.FORBIDDEN,
                    Unpooled.wrappedBuffer("Origin forbidden".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            response.headers().set("Vary", "Origin");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return false;
        }

        return admitVisitor(ctx, GameConnectionLimits.Settings.from(config));
    }

    /**
     * Counts the game connection against its visitor, who may hold only so many open at once. The count is
     * released when the channel closes.
     */
    private static boolean admitVisitor(ChannelHandlerContext ctx, GameConnectionLimits.Settings settings) {
        String peerIp =
                ctx.channel().remoteAddress() instanceof InetSocketAddress address && address.getAddress() != null
                        ? address.getAddress().getHostAddress()
                        : null;
        String visitor = GameConnectionLimits.visitorOf(
                ctx.channel().attr(GameServerAttributes.WS_IP).get(), peerIp, AuthHttpUtil.isTrustedProxy(ctx));
        GameConnectionLimits limits = GameConnectionLimits.shared();
        if (!limits.tryOpen(visitor, settings)) {
            LOGGER.warn(
                    "WebSocket upgrade rejected - {} already holds {} game connections",
                    visitor,
                    settings.maxConnections());
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.TOO_MANY_REQUESTS,
                    Unpooled.wrappedBuffer("Too many connections".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return false;
        }
        if (visitor != null) {
            ctx.channel().attr(GameServerAttributes.VISITOR).set(visitor);
            ctx.channel().closeFuture().addListener(future -> limits.close(visitor));
        }
        return true;
    }

    private static void captureForwardedIp(ChannelHandlerContext ctx, HttpMessage req, ConfigurationManager config) {
        String ipHeader = config.getValue("ws.ip.header", "");
        // Only honour the forwarded-IP header from a trusted reverse proxy,
        // otherwise the game-session IP (used for bans/rate-limits) is spoofable.
        // Read again for every request: a proxy reuses one connection for several clients, so an IP kept
        // from an earlier request belonged to someone else.
        String ip = "";
        if (!ipHeader.isEmpty()
                && req.headers().contains(ipHeader)
                && AuthHttpUtil.shouldHonorForwardedHeader(ctx, ipHeader)) {
            ip = AuthHttpUtil.resolveForwardedClientIp(req.headers().get(ipHeader));
        }
        ctx.channel().attr(GameServerAttributes.WS_IP).set(ip.isEmpty() ? null : ip);
    }

    private static boolean isWebSocketUpgrade(HttpMessage req) {
        String upgrade = req.headers().get(HttpHeaderNames.UPGRADE);
        if (upgrade == null || !"websocket".equalsIgnoreCase(upgrade)) return false;

        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        if (connection == null) return false;

        for (String token : connection.split(",")) {
            if ("upgrade".equalsIgnoreCase(token.trim())) return true;
        }
        return false;
    }

    private static String getDomainNameFromUrl(String url) throws Exception {
        URI uri = new URI(url);
        String domain = uri.getHost();
        if (domain == null) return "error";
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    private static boolean isWhitelisted(String toCheck, String[] whitelist) {
        for (String entry : whitelist) {
            String trimmed = entry.trim();
            if (trimmed.equals("*")) {
                return true;
            }
            if (trimmed.startsWith("*")) {
                String suffix = trimmed.substring(1);
                if (toCheck.endsWith(suffix) || ("." + toCheck).equals(suffix)) {
                    return true;
                }
            } else {
                if (toCheck.equals(trimmed)) {
                    return true;
                }
            }
        }
        return false;
    }
}
