package com.eu.habbo.networking.gameserver.handlers;

import com.eu.habbo.Emulator;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import com.eu.habbo.networking.gameserver.auth.AuthHttpUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class WebSocketHttpHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketHttpHandler.class);
    private static final String ORIGIN_HEADER = "Origin";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpMessage) {
            if (!handleHttpRequest(ctx, (HttpMessage) msg)) {
                ReferenceCountUtil.release(msg);
                return;
            }
        }
        super.channelRead(ctx, msg);
        ctx.pipeline().remove(this);
    }

    private boolean handleHttpRequest(ChannelHandlerContext ctx, HttpMessage req) {
        captureForwardedIp(ctx, req);

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

        String whitelist = Emulator.getConfig().getValue("ws.whitelist", "localhost");
        if (!isWhitelisted(origin, whitelist.split(","))) {
            LOGGER.warn("WebSocket upgrade rejected — origin '{}' not in ws.whitelist='{}'",
                    req.headers().get(ORIGIN_HEADER), whitelist);

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.FORBIDDEN,
                    Unpooled.wrappedBuffer("Origin forbidden".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
            response.headers().set("Vary", "Origin");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return false;
        }

        return true;
    }

    private static void captureForwardedIp(ChannelHandlerContext ctx, HttpMessage req) {
        String ipHeader = Emulator.getConfig().getValue("ws.ip.header", "");
        // Only honour the forwarded-IP header from a trusted reverse proxy,
        // otherwise the game-session IP (used for bans/rate-limits) is spoofable.
        if (!ipHeader.isEmpty() && req.headers().contains(ipHeader) && AuthHttpUtil.isTrustedProxy(ctx)) {
            String ip = req.headers().get(ipHeader);
            if (ip != null && !ip.isEmpty()) {
                int comma = ip.indexOf(',');
                ctx.channel().attr(GameServerAttributes.WS_IP).set((comma > 0 ? ip.substring(0, comma) : ip).trim());
            }
        }
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
