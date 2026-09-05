package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes one room variable for the holder of a wired web-API key.
 *
 * <p>This is deliberately not part of the CMS API. That one admits callers by address first and
 * authenticates a single hotel-wide operator second, which is right for a control plane. This one is
 * the opposite shape: the caller is an arbitrary third party a room owner handed a key to, the key
 * is the whole credential, and what it unlocks is a single variable in a single room. Sharing the
 * CMS admission would either lock out the callers this exists for or widen the CMS surface to
 * anyone on the internet.
 *
 * <p>It ships disabled. A hotel that never turns it on serves 404 here, so a key that leaks before
 * the operator has thought about exposure is worth nothing.
 */
public class WiredVariableApiHandler extends ChannelInboundHandlerAdapter {
    static final String BASE_PATH = "/api/wired";
    static final String VARIABLE_PATH = "/api/wired/variable";

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredVariableApiHandler.class);
    private static final int DEFAULT_MAX_PAYLOAD_BYTES = 4096;

    private static final LoadingCache<String, RateLimiter> RATE_LIMITERS = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(WiredVariableApiHandler::newRateLimiter);

    public static void logStartupStatus(String host, int port) {
        if (!enabled()) {
            return;
        }
        LOGGER.info("Started wired variable API on {}:{}{}", host, port, BASE_PATH);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!path.equals(BASE_PATH) && !path.equals(VARIABLE_PATH)) {
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
            sendEmpty(ctx, req, HttpResponseStatus.NO_CONTENT);
            return;
        }

        if (!enabled()) {
            // 404 rather than 403: a hotel that has not turned this on should not even confirm the
            // route exists.
            sendEnvelope(ctx, req, HttpResponseStatus.NOT_FOUND, 1, "wired api disabled");
            return;
        }

        if (!acquirePermit(clientIp(ctx))) {
            sendEnvelope(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, 1, "rate limited");
            return;
        }

        if (path.equals(BASE_PATH)) {
            if (req.method() != HttpMethod.GET) {
                sendEnvelope(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, 1, "use GET");
                return;
            }
            JsonObject index = new JsonObject();
            index.addProperty("status", 0);
            index.addProperty("message", "wired variable api");
            index.addProperty("read", "GET " + VARIABLE_PATH + "?key=<read key>");
            index.addProperty("write", "POST " + VARIABLE_PATH + " {\"key\":\"<write key>\",\"value\":<int>}");
            sendJson(ctx, req, HttpResponseStatus.OK, index);
            return;
        }

        if (req.method() == HttpMethod.GET) {
            handleRead(ctx, req);
            return;
        }
        if (req.method() == HttpMethod.POST) {
            handleWrite(ctx, req);
            return;
        }
        sendEnvelope(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, 1, "use GET or POST");
    }

    private void handleRead(ChannelHandlerContext ctx, FullHttpRequest req) {
        List<String> keys = new QueryStringDecoder(req.uri()).parameters().get("key");
        String key = (keys == null || keys.isEmpty()) ? null : keys.get(0);

        WiredExtraVariableWebApi.Lookup lookup = WiredExtraVariableWebApi.resolve(key);
        if (lookup == null) {
            sendEnvelope(ctx, req, HttpResponseStatus.FORBIDDEN, 1, "unknown key");
            return;
        }

        Room room = roomOf(lookup.addon());
        if (room == null) {
            sendEnvelope(ctx, req, HttpResponseStatus.CONFLICT, 1, "room not loaded");
            return;
        }

        RoomVariableManager variables = room.getRoomVariableManager();
        if (variables == null || !variables.hasVariable(lookup.addon().getVariableItemId())) {
            sendEnvelope(ctx, req, HttpResponseStatus.NOT_FOUND, 1, "variable not found");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("status", 0);
        body.addProperty("roomId", room.getId());
        body.addProperty("variableId", lookup.addon().getVariableItemId());
        body.addProperty("value", variables.getCurrentValue(lookup.addon().getVariableItemId()));
        body.addProperty("writable", lookup.addon().isWriteEnabled());
        sendJson(ctx, req, HttpResponseStatus.OK, body);
    }

    private void handleWrite(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.content().readableBytes() > maxPayloadBytes()) {
            sendEnvelope(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, 1, "payload too large");
            return;
        }

        JsonObject payload;
        try {
            payload = JsonParser.parseString(req.content().toString(StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            sendEnvelope(ctx, req, HttpResponseStatus.BAD_REQUEST, 1, "malformed json");
            return;
        }

        String key = payload.has("key") ? payload.get("key").getAsString() : null;
        WiredExtraVariableWebApi.Lookup lookup = WiredExtraVariableWebApi.resolve(key);
        if (lookup == null) {
            sendEnvelope(ctx, req, HttpResponseStatus.FORBIDDEN, 1, "unknown key");
            return;
        }
        // The read key never writes, whatever it is sent to.
        if (lookup.access() != WiredExtraVariableWebApi.Access.WRITE) {
            sendEnvelope(ctx, req, HttpResponseStatus.FORBIDDEN, 1, "read key");
            return;
        }
        if (!lookup.addon().isWriteEnabled()) {
            sendEnvelope(ctx, req, HttpResponseStatus.FORBIDDEN, 1, "writing disabled");
            return;
        }

        int value;
        try {
            value = payload.get("value").getAsInt();
        } catch (Exception e) {
            sendEnvelope(ctx, req, HttpResponseStatus.BAD_REQUEST, 1, "value must be an integer");
            return;
        }

        Room room = roomOf(lookup.addon());
        if (room == null) {
            sendEnvelope(ctx, req, HttpResponseStatus.CONFLICT, 1, "room not loaded");
            return;
        }

        RoomVariableManager variables = room.getRoomVariableManager();
        if (variables == null || !variables.hasVariable(lookup.addon().getVariableItemId())) {
            sendEnvelope(ctx, req, HttpResponseStatus.NOT_FOUND, 1, "variable not found");
            return;
        }

        // updateVariableValue is the same path a wired box takes, so everything watching the
        // variable - triggers, text output, the variables panel - reacts exactly as it would in room.
        if (!variables.updateVariableValue(lookup.addon().getVariableItemId(), value)) {
            sendEnvelope(ctx, req, HttpResponseStatus.CONFLICT, 1, "value rejected");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("status", 0);
        body.addProperty("roomId", room.getId());
        body.addProperty("variableId", lookup.addon().getVariableItemId());
        body.addProperty("value", variables.getCurrentValue(lookup.addon().getVariableItemId()));
        sendJson(ctx, req, HttpResponseStatus.OK, body);
    }

    private static Room roomOf(WiredExtraVariableWebApi addon) {
        // Only an already-loaded room answers. Loading one from an unauthenticated HTTP call would
        // let anyone holding a key pull rooms into memory.
        return Emulator.getGameEnvironment().getRoomManager().getRoom(addon.getRoomId());
    }

    static boolean enabled() {
        return Emulator.getConfig() != null && Emulator.getConfig().getBoolean("wired.api.enabled", false);
    }

    private static boolean acquirePermit(String ip) {
        if (!Emulator.getConfig().getBoolean("wired.api.rate_limit.enabled", true)) {
            return true;
        }
        return RATE_LIMITERS.get(ip).acquirePermission();
    }

    private static RateLimiter newRateLimiter(String ip) {
        RateLimiterConfig limiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(Math.max(1, Emulator.getConfig().getInt("wired.api.rate_limit.limit_for_period", 60)))
                .limitRefreshPeriod(Duration.ofMillis(
                        Math.max(100, Emulator.getConfig().getInt("wired.api.rate_limit.refresh_period_ms", 1000))))
                .timeoutDuration(Duration.ZERO)
                .build();
        return RateLimiter.of("wired-api-" + ip, limiterConfig);
    }

    private static int maxPayloadBytes() {
        int configured = Emulator.getConfig().getInt("wired.api.max_payload_bytes", DEFAULT_MAX_PAYLOAD_BYTES);
        return configured > 0 ? configured : DEFAULT_MAX_PAYLOAD_BYTES;
    }

    private static String clientIp(ChannelHandlerContext ctx) {
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress address && address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private static void sendEnvelope(
            ChannelHandlerContext ctx,
            FullHttpRequest req,
            HttpResponseStatus status,
            int envelopeStatus,
            String message) {
        JsonObject body = new JsonObject();
        body.addProperty("status", envelopeStatus);
        body.addProperty("message", message == null ? "" : message);
        sendJson(ctx, req, status, body);
    }

    private static void sendJson(
            ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, JsonObject body) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        finish(ctx, req, response);
    }

    private static void sendEmpty(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        finish(ctx, req, response);
    }

    private static void finish(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse response) {
        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        boolean keepAlive = connection == null || !"close".equalsIgnoreCase(connection);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
