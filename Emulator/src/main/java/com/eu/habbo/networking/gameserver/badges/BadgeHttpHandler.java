package com.eu.habbo.networking.gameserver.badges;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.custombadge.CustomBadge;
import com.eu.habbo.habbohotel.users.custombadge.CustomBadgeException;
import com.eu.habbo.habbohotel.users.custombadge.CustomBadgeManager;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import com.eu.habbo.networking.gameserver.auth.AccessTokenService;
import com.eu.habbo.networking.gameserver.auth.AuthRateLimiter;
import com.eu.habbo.networking.gameserver.auth.CorsOriginGate;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class BadgeHttpHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeHttpHandler.class);

    private static final String BASE_PATH = "/api/badges/custom";
    private static final int MAX_BODY_BYTES = 128 * 1024;

    private static volatile JsonObject cachedTextsResponse = null;
    private static volatile long cachedTextsVersion = -1L;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!path.equals(BASE_PATH) && !path.startsWith(BASE_PATH + "/")) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            handle(ctx, req, path);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        if (req.method() == HttpMethod.OPTIONS) {
            sendCors(ctx, req);
            return;
        }

        if (path.equals(BASE_PATH + "/texts")) {
            if (req.method() == HttpMethod.GET || req.method() == HttpMethod.HEAD) {
                String ip = resolveClientIp(ctx, req);
                if (!AuthRateLimiter.tryProbe(ip)) {
                    long secs = AuthRateLimiter.secondsUntilProbeReset(ip);
                    sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS,
                            error("Too many requests. Try again in " + secs + "s."));
                    return;
                }
                handleTexts(ctx, req);
                return;
            }
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("Use GET."));
            return;
        }

        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, error("Authentication required."));
            return;
        }

        if (req.content().readableBytes() > MAX_BODY_BYTES) {
            sendJson(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, error("Payload too large."));
            return;
        }

        String trailing = path.length() > BASE_PATH.length() ? path.substring(BASE_PATH.length() + 1) : "";

        try {
            if (trailing.isEmpty()) {
                if (req.method() == HttpMethod.GET || req.method() == HttpMethod.HEAD) {
                    handleList(ctx, req, userId);
                    return;
                }
                if (req.method() == HttpMethod.POST) {
                    handleCreate(ctx, req, userId);
                    return;
                }
                sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("Use GET or POST."));
                return;
            }

            String badgeId = trailing;
            CustomBadgeManager manager = Emulator.getGameEnvironment().getCustomBadgeManager();
            if (!manager.isCustomBadgeId(badgeId)) {
                sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Invalid badge id."));
                return;
            }

            if (req.method() == HttpMethod.PUT || req.method() == HttpMethod.POST) {
                handleUpdate(ctx, req, userId, badgeId);
                return;
            }
            if (req.method() == HttpMethod.DELETE) {
                handleDelete(ctx, req, userId, badgeId);
                return;
            }
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("Use PUT or DELETE."));
        } catch (Exception e) {
            LOGGER.error("[badges/custom] unexpected error path=" + path, e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("Server error."));
        }
    }

    private void handleTexts(ChannelHandlerContext ctx, FullHttpRequest req) {
        CustomBadgeManager manager = Emulator.getGameEnvironment().getCustomBadgeManager();
        long version = manager.getTextCacheVersion();
        JsonObject ok = cachedTextsResponse;
        if (ok == null || cachedTextsVersion != version) {
            java.util.Map<String, CustomBadgeManager.BadgeText> cache = manager.getTextCache();
            JsonObject texts = new JsonObject();
            for (java.util.Map.Entry<String, CustomBadgeManager.BadgeText> entry : cache.entrySet()) {
                String badgeId = entry.getKey();
                CustomBadgeManager.BadgeText value = entry.getValue();
                texts.addProperty("badge_name_" + badgeId, value.name);
                texts.addProperty("badge_desc_" + badgeId, value.description);
            }
            JsonObject built = new JsonObject();
            built.add("texts", texts);
            built.addProperty("count", cache.size());
            built.addProperty("version", version);
            cachedTextsResponse = built;
            cachedTextsVersion = version;
            ok = built;
        }
        sendJsonCached(ctx, req, HttpResponseStatus.OK, ok);
    }

    private void handleList(ChannelHandlerContext ctx, FullHttpRequest req, int userId) {
        CustomBadgeManager manager = Emulator.getGameEnvironment().getCustomBadgeManager();
        List<CustomBadge> badges = manager.listForUser(userId);

        JsonArray arr = new JsonArray();
        for (CustomBadge b : badges) arr.add(toJson(b, manager));

        JsonObject ok = new JsonObject();
        ok.add("badges", arr);
        ok.addProperty("max", CustomBadgeManager.MAX_PER_USER);
        ok.addProperty("badgeWidth", CustomBadgeManager.BADGE_WIDTH);
        ok.addProperty("badgeHeight", CustomBadgeManager.BADGE_HEIGHT);
        ok.addProperty("maxBadgeSizeBytes", CustomBadgeManager.MAX_BADGE_SIZE_BYTES);
        if (manager.getSettings() != null) {
            ok.addProperty("priceBadge", manager.getSettings().getPriceBadge());
            ok.addProperty("currencyType", manager.getSettings().getCurrencyType());
        }
        sendJson(ctx, req, HttpResponseStatus.OK, ok);
    }

    private void handleCreate(ChannelHandlerContext ctx, FullHttpRequest req, int userId) {
        JsonObject body = readJsonBody(req);
        if (body == null) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Invalid JSON body."));
            return;
        }

        byte[] png = decodeImage(body);
        if (png == null) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Missing or invalid image."));
            return;
        }

        String name = optString(body, "name");
        String description = optString(body, "description");

        CustomBadgeManager manager = Emulator.getGameEnvironment().getCustomBadgeManager();
        try {
            CustomBadge created = manager.create(userId, name, description, png);
            sendJson(ctx, req, HttpResponseStatus.CREATED, toJson(created, manager));
        } catch (CustomBadgeException e) {
            sendJson(ctx, req, statusFor(e), error(e.getMessage(), e.getCode()));
        }
    }

    private void handleUpdate(ChannelHandlerContext ctx, FullHttpRequest req, int userId, String badgeId) {
        JsonObject body = readJsonBody(req);
        if (body == null) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Invalid JSON body."));
            return;
        }

        byte[] png = decodeImage(body);
        if (png == null) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Missing or invalid image."));
            return;
        }

        String name = optString(body, "name");
        String description = optString(body, "description");

        CustomBadgeManager manager = Emulator.getGameEnvironment().getCustomBadgeManager();
        try {
            CustomBadge updated = manager.update(userId, badgeId, name, description, png);
            sendJson(ctx, req, HttpResponseStatus.OK, toJson(updated, manager));
        } catch (CustomBadgeException e) {
            sendJson(ctx, req, statusFor(e), error(e.getMessage(), e.getCode()));
        }
    }

    private void handleDelete(ChannelHandlerContext ctx, FullHttpRequest req, int userId, String badgeId) {
        CustomBadgeManager manager = Emulator.getGameEnvironment().getCustomBadgeManager();
        try {
            manager.delete(userId, badgeId);
            JsonObject ok = new JsonObject();
            ok.addProperty("deleted", badgeId);
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (CustomBadgeException e) {
            sendJson(ctx, req, statusFor(e), error(e.getMessage(), e.getCode()));
        }
    }

    private static byte[] decodeImage(JsonObject body) {
        if (!body.has("image")) return null;
        try {
            String raw = body.get("image").getAsString();
            if (raw == null || raw.isEmpty()) return null;
            int comma = raw.indexOf(',');
            String b64 = raw.startsWith("data:") && comma >= 0 ? raw.substring(comma + 1) : raw;
            return Base64.getDecoder().decode(b64.replaceAll("\\s+", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject readJsonBody(FullHttpRequest req) {
        try {
            String text = req.content().toString(StandardCharsets.UTF_8);
            if (text.isEmpty()) return new JsonObject();
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String optString(JsonObject body, String key) {
        if (body == null || !body.has(key) || body.get(key).isJsonNull()) return "";
        try { return body.get(key).getAsString(); }
        catch (Exception e) { return ""; }
    }

    private static int authenticate(FullHttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (header == null || header.isEmpty()) return 0;
        String token;
        if (header.startsWith("Bearer ")) token = header.substring(7).trim();
        else token = header.trim();
        return AccessTokenService.verify(token);
    }

    private static HttpResponseStatus statusFor(CustomBadgeException e) {
        return switch (e.getCode()) {
            case "not_found" -> HttpResponseStatus.NOT_FOUND;
            case "insufficient_funds" -> HttpResponseStatus.PAYMENT_REQUIRED;
            case "must_be_online" -> HttpResponseStatus.CONFLICT;
            case "rate_limited" -> HttpResponseStatus.TOO_MANY_REQUESTS;
            case "limit_reached", "wrong_dimensions", "too_large", "empty", "invalid_image", "not_configured" ->
                    HttpResponseStatus.BAD_REQUEST;
            default -> HttpResponseStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static JsonObject toJson(CustomBadge badge, CustomBadgeManager manager) {
        JsonObject obj = new JsonObject();
        obj.addProperty("badgeId", badge.getBadgeId());
        obj.addProperty("badgeCode", badge.getBadgeId());
        obj.addProperty("name", badge.getBadgeName());
        obj.addProperty("description", badge.getBadgeDescription());
        obj.addProperty("dateCreated", badge.getDateCreated());
        obj.addProperty("dateEdit", badge.getDateEdit());
        obj.addProperty("url", manager.publicUrlFor(badge.getBadgeId()));
        return obj;
    }

    private static JsonObject error(String message) {
        return error(message, null);
    }

    private static JsonObject error(String message, String code) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        if (code != null) obj.addProperty("code", code);
        return obj;
    }

    private static void sendJsonCached(ChannelHandlerContext ctx, FullHttpRequest req,
                                       HttpResponseStatus status, JsonObject body) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=30");
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendJson(ChannelHandlerContext ctx, FullHttpRequest req,
                                 HttpResponseStatus status, JsonObject body) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendCors(ChannelHandlerContext ctx, FullHttpRequest req) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void applyCors(FullHttpRequest req, FullHttpResponse response) {
        // Vary is emitted for every response so shared caches never serve a
        // CORS-approved response body to a request with a different Origin.
        response.headers().set("Vary", "Origin");

        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Credentials", "true");
            response.headers().set("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, OPTIONS");
            response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        }
    }

    private static boolean isKeepAlive(FullHttpRequest req) {
        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        if (connection != null && connection.equalsIgnoreCase("close")) return false;
        if (connection != null && connection.equalsIgnoreCase("keep-alive")) return true;
        return req.protocolVersion().isKeepAliveDefault();
    }

    @SuppressWarnings("unused")
    private static String resolveClientIp(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (ctx.channel().attr(GameServerAttributes.WS_IP).get() != null) {
            return ctx.channel().attr(GameServerAttributes.WS_IP).get();
        }
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress addr) {
            return addr.getAddress().getHostAddress();
        }
        return "";
    }
}
