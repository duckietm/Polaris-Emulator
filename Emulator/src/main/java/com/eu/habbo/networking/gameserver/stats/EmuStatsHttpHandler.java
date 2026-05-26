package com.eu.habbo.networking.gameserver.stats;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.monitoring.EmulatorStatsService;
import com.eu.habbo.networking.gameserver.auth.AccessTokenService;
import com.eu.habbo.networking.gameserver.auth.CorsOriginGate;
import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.StandardCharsets;

public class EmuStatsHttpHandler extends ChannelInboundHandlerAdapter {
    private static final String BASE_PATH = "/api/emustats";
    private static final Gson GSON = new Gson();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!BASE_PATH.equals(path)) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            handle(ctx, req);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() == HttpMethod.OPTIONS) {
            sendCors(ctx, req);
            return;
        }

        if (req.method() != HttpMethod.GET && req.method() != HttpMethod.HEAD) {
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, "{\"error\":\"Use GET.\"}");
            return;
        }

        int userId = authenticate(req);
        if (userId <= 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, "{\"error\":\"Unauthorized.\"}");
            return;
        }

        Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(userId);
        if (habbo == null || !habbo.hasPermission(Permission.ACC_MODTOOL_ROOM_INFO)) {
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, "{\"error\":\"Forbidden.\"}");
            return;
        }

        EmulatorStatsService.Snapshot snapshot = EmulatorStatsService.collectSnapshot();
        sendJson(ctx, req, HttpResponseStatus.OK, GSON.toJson(snapshot));
    }

    private static int authenticate(FullHttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (header == null || header.isBlank()) return 0;

        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        return AccessTokenService.verify(token);
    }

    private static void sendCors(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!CorsOriginGate.isAllowed(req)) {
            FullHttpResponse forbidden = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
            forbidden.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(forbidden).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyCors(req, response);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendJson(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String json) {
        boolean headRequest = req.method() == HttpMethod.HEAD;
        byte[] bytes = (json == null ? "{}" : json).getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = headRequest
                ? new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status)
                : new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void applyCors(FullHttpRequest req, FullHttpResponse response) {
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization, Content-Type, X-Requested-With, X-Nitro-Key, X-Nitro-Api");
            response.headers().set(HttpHeaderNames.VARY, "Origin");
        }
    }
}
