package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.applyCors;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.errorPayload;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.isKeepAlive;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.sendJson;

final class StaticContentEndpoints {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticContentEndpoints.class);

    private static final long NEWS_CACHE_TTL_MS = 30_000L;
    private static final int NEWS_IMAGE_MAX_BYTES = 512 * 1024;
    private static volatile NewsCacheEntry NEWS_CACHE = null;

    private static final class NewsCacheEntry {
        final byte[] jsonBytes;
        final long expiresAt;

        NewsCacheEntry(byte[] j, long e) {
            jsonBytes = j;
            expiresAt = e;
        }
    }

    private StaticContentEndpoints() {
    }

    static void handleRoomTemplates(ChannelHandlerContext ctx, FullHttpRequest req) {
        JsonArray templates = new JsonArray();
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT template_id, title, description, thumbnail " +
                             "FROM room_templates WHERE enabled = '1' " +
                             "ORDER BY sort_order ASC, template_id ASC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject t = new JsonObject();
                    t.addProperty("templateId", rs.getInt("template_id"));
                    t.addProperty("title", rs.getString("title"));
                    t.addProperty("description", rs.getString("description"));
                    t.addProperty("thumbnail", rs.getString("thumbnail"));
                    templates.add(t);
                }
            }
        } catch (Exception e) {
            LOGGER.error("room-templates list failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
            return;
        }
        JsonObject res = new JsonObject();
        res.add("templates", templates);
        sendJson(ctx, req, HttpResponseStatus.OK, res);
    }

    static void handleNews(ChannelHandlerContext ctx, FullHttpRequest req) {
        long now = System.currentTimeMillis();
        NewsCacheEntry cached = NEWS_CACHE;

        if (cached == null || cached.expiresAt < now) {
            JsonArray items = new JsonArray();
            int limit = Math.max(1, Math.min(20, Emulator.getConfig().getInt("login.news.limit", 5)));
            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT id, title, body, image, link_text, link_url " +
                                 "FROM ui_news WHERE enabled = 1 " +
                                 "ORDER BY sort_order ASC, id DESC LIMIT ?")) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        JsonObject n = new JsonObject();
                        n.addProperty("id", id);
                        n.addProperty("title", rs.getString("title"));
                        n.addProperty("body", rs.getString("body"));

                        String image = rs.getString("image");
                        if (image != null && image.length() > NEWS_IMAGE_MAX_BYTES) {
                            LOGGER.warn("ui_news id={} image is {} bytes (>{}KB cap), omitting in response",
                                    id, image.length(), NEWS_IMAGE_MAX_BYTES / 1024);
                            image = null;
                        }
                        n.addProperty("image", image);

                        n.addProperty("linkText", rs.getString("link_text"));
                        n.addProperty("linkUrl", rs.getString("link_url"));
                        items.add(n);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("ui_news list failed", e);
                sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
                return;
            }

            JsonObject res = new JsonObject();
            res.add("news", items);
            byte[] bytes = res.toString().getBytes(StandardCharsets.UTF_8);
            cached = new NewsCacheEntry(bytes, now + NEWS_CACHE_TTL_MS);
            NEWS_CACHE = cached;
        }

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(cached.jsonBytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, cached.jsonBytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=30");
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    static void handleServerKey(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            JsonObject ok = new JsonObject();
            ok.addProperty("publicKey", com.eu.habbo.networking.gameserver.crypto.CryptoSigningKeyManager.publicKeyBase64());
            ok.addProperty("algorithm", "ECDSA-P256-SHA256");
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (Exception e) {
            LOGGER.error("server-key fetch failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }
}
