package com.eu.habbo.networking.gameserver.cms;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.command.CommandRegistry;
import com.eu.habbo.messages.command.CommandResult;
import com.eu.habbo.networking.gameserver.auth.CidrRange;
import com.eu.habbo.networking.rconserver.RCONServer;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.gson.JsonArray;
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
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP transport for the administrative command surface, intended to replace the
 * RCON TCP listener for external CMS software while running side by side with it.
 *
 * <p>Two-tier access control:
 * <ol>
 *   <li><b>Network</b> — the direct peer must match {@code cms.api.allowed}
 *       (loopback by default; exact IPs or CIDR blocks). Optionally requires TLS.</li>
 *   <li><b>Request</b> — an HMAC signature proves the caller holds a configured
 *       key's secret, with per-key scopes limiting which commands it may run.</li>
 * </ol>
 *
 * Dispatch reuses the shared {@link CommandRegistry}, so commands, validation and
 * the {@code {status, message}} envelope are identical to RCON.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET  /api/cms/ping} — network-gated liveness probe.</li>
 *   <li>{@code GET  /api/cms/commands} — authenticated list of dispatchable keys.</li>
 *   <li>{@code POST /api/cms/command} — authenticated, scoped command dispatch.</li>
 * </ul>
 */
public class CmsApiHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmsApiHandler.class);

    static final String BASE_PATH = "/api/cms";
    static final String PING_PATH = "/api/cms/ping";
    static final String COMMANDS_PATH = "/api/cms/commands";
    static final String COMMAND_PATH = "/api/cms/command";

    private static final int DEFAULT_MAX_PAYLOAD_BYTES = 64 * 1024;

    // Rebuilt whenever cms.api.keys changes, so config reloads take effect without
    // a restart. The snapshot pairs the raw config string with its parsed store +
    // authenticator so we only reparse on change.
    private record AuthSnapshot(String rawKeys, long skew, long ttl, CmsApiAuthenticator authenticator) {}

    // Process-wide, not per-connection: a new CmsApiHandler is created for every
    // channel, so the nonce cache (inside the authenticator) and the per-IP rate
    // limiters must be shared statically — otherwise replay and rate limits would
    // reset on every new TCP connection. Final references keep these off the
    // mutable-static-field baseline.
    private static final AtomicReference<AuthSnapshot> AUTH_SNAPSHOT = new AtomicReference<>();
    private static final LoadingCache<String, RateLimiter> RATE_LIMITERS = Caffeine.newBuilder()
            .maximumSize(1024)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(CmsApiHandler::newRateLimiter);

    /**
     * Logs a one-line startup status for the CMS API, mirroring the RCON listener's
     * "Started ..." banner. The API has no dedicated socket — it rides on the
     * WebSocket listener — so the host/port are passed in by the game server.
     * Stays silent when the API is disabled.
     */
    public static void logStartupStatus(String host, int port) {
        var config = config();
        if (config == null || !config.getBoolean("cms.api.enabled", false)) {
            return;
        }
        int keys = CmsApiKeyStore.parse(CmsApiSettings.get().getValue("cms.api.keys", ""))
                .size();
        LOGGER.info(
                "Started CMS API on {}:{}{} ({} key(s), allowed={})",
                host,
                port,
                BASE_PATH,
                keys,
                config.getValue("cms.api.allowed", "127.0.0.1;::1"));
        if (keys == 0) {
            LOGGER.warn("CMS API is enabled but cms.api.keys is empty (set it in the emulator_api table); "
                    + "requests will be rejected until a key is added");
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!isOurRoute(path)) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            handle(ctx, req, path);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private static boolean isOurRoute(String path) {
        return path.equals(BASE_PATH)
                || path.equals(PING_PATH)
                || path.equals(COMMANDS_PATH)
                || path.equals(COMMAND_PATH);
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        if (req.method() == HttpMethod.OPTIONS) {
            sendEmpty(ctx, req, HttpResponseStatus.NO_CONTENT);
            return;
        }

        if (!config().getBoolean("cms.api.enabled", false)) {
            sendEnvelope(ctx, req, HttpResponseStatus.NOT_FOUND, 1, "cms api disabled");
            return;
        }

        String ip = clientIp(ctx);

        // Tier 1 — network admission on the direct peer.
        if (!isAddressAllowed(ip)) {
            LOGGER.warn("[cms-api] rejected {} from disallowed address {}", path, ip);
            sendEnvelope(ctx, req, HttpResponseStatus.FORBIDDEN, 1, "forbidden");
            return;
        }
        if (CmsApiSettings.get().getBoolean("cms.api.require_tls", false) && !isSecure(ctx) && !isLoopback(ip)) {
            sendEnvelope(ctx, req, HttpResponseStatus.FORBIDDEN, 1, "tls required");
            return;
        }
        if (!acquirePermit(ip)) {
            sendEnvelope(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, 1, "rate limited");
            return;
        }

        if (path.equals(BASE_PATH)) {
            if (!isGet(req)) {
                sendEnvelope(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, 1, "use GET");
                return;
            }
            sendIndex(ctx, req);
            return;
        }

        if (path.equals(PING_PATH)) {
            if (!isGet(req)) {
                sendEnvelope(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, 1, "use GET");
                return;
            }
            sendEnvelope(ctx, req, HttpResponseStatus.OK, 0, "pong");
            return;
        }

        String requiredMethod = path.equals(COMMAND_PATH) ? "POST" : "GET";
        boolean methodOk = path.equals(COMMAND_PATH) ? req.method() == HttpMethod.POST : isGet(req);
        if (!methodOk) {
            sendEnvelope(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, 1, "use " + requiredMethod);
            return;
        }

        int maxBytes = maxPayloadBytes();
        if (req.content().readableBytes() > maxBytes) {
            sendEnvelope(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, 1, "payload too large");
            return;
        }
        String rawBody = req.content().toString(StandardCharsets.UTF_8);

        // Tier 2 — request authentication.
        CmsApiAuthenticator.Result auth = authenticator()
                .authenticate(
                        header(req, "X-Cms-Key"),
                        header(req, "X-Cms-Timestamp"),
                        header(req, "X-Cms-Nonce"),
                        header(req, "X-Cms-Signature"),
                        rawBody);
        if (auth.status() != CmsApiAuthenticator.Status.OK) {
            HttpResponseStatus http = auth.status() == CmsApiAuthenticator.Status.REPLAY
                    ? HttpResponseStatus.CONFLICT
                    : HttpResponseStatus.UNAUTHORIZED;
            LOGGER.warn("[cms-api] auth {} for {} from {} (key={})", auth.status(), path, ip, header(req, "X-Cms-Key"));
            sendEnvelope(ctx, req, http, 1, "unauthorized");
            return;
        }
        CmsApiKey key = auth.key();

        if (path.equals(COMMANDS_PATH)) {
            sendCommandList(ctx, req, key);
            return;
        }

        dispatchCommand(ctx, req, rawBody, key, ip);
    }

    private void dispatchCommand(
            ChannelHandlerContext ctx, FullHttpRequest req, String rawBody, CmsApiKey key, String ip) {
        JsonObject body;
        try {
            body = rawBody.isBlank()
                    ? new JsonObject()
                    : JsonParser.parseString(rawBody).getAsJsonObject();
        } catch (Exception e) {
            sendEnvelope(ctx, req, HttpResponseStatus.BAD_REQUEST, 1, "invalid json body");
            return;
        }

        String commandKey = body.has("key") && !body.get("key").isJsonNull()
                ? body.get("key").getAsString()
                : "";
        if (commandKey.isBlank()) {
            sendEnvelope(ctx, req, HttpResponseStatus.BAD_REQUEST, 1, "missing command key");
            return;
        }

        if (!CmsCommandScopes.isAllowed(key.scopes(), commandKey)) {
            LOGGER.warn("[cms-api] key={} denied cmd={} (scope) ip={}", key.keyId(), commandKey, ip);
            sendEnvelope(ctx, req, HttpResponseStatus.FORBIDDEN, 1, "command not in scope");
            return;
        }

        CommandRegistry registry = registry();
        if (registry == null) {
            sendEnvelope(ctx, req, HttpResponseStatus.SERVICE_UNAVAILABLE, 4, "command registry unavailable");
            return;
        }

        String data = body.has("data") && !body.get("data").isJsonNull()
                ? body.get("data").toString()
                : "{}";
        CommandResult result = registry.dispatch(commandKey, data);

        HttpResponseStatus http = httpStatusFor(result);
        LOGGER.info(
                "[cms-api] key={} cmd={} ip={} -> http={} status={} msg={}",
                key.keyId(),
                CommandRegistry.normalize(commandKey),
                ip,
                http.code(),
                result.status(),
                result.message());
        sendEnvelope(ctx, req, http, result.status(), result.message());
    }

    /**
     * Network-gated discovery index: lists every route with whether it needs an API
     * key. No key required (only the Tier-1 allow-list), so an operator can curl the
     * base path to see the surface. Reveals only route names — already public in the
     * contract/docs — never the actual command list (that stays behind auth + scope).
     */
    private void sendIndex(ChannelHandlerContext ctx, FullHttpRequest req) {
        JsonArray endpoints = new JsonArray();
        endpoints.add(endpoint("GET", BASE_PATH, false, "This index of available calls."));
        endpoints.add(endpoint("GET", PING_PATH, false, "Liveness probe; returns pong."));
        endpoints.add(endpoint("GET", COMMANDS_PATH, true, "List the commands your key is scoped for."));
        endpoints.add(endpoint("POST", COMMAND_PATH, true, "Dispatch a command: body {key, data}."));

        JsonObject payload = new JsonObject();
        payload.addProperty("status", 0);
        payload.addProperty("message", "");
        payload.addProperty("service", "cms-api");
        payload.addProperty("version", 1);
        payload.add("endpoints", endpoints);
        sendJson(ctx, req, HttpResponseStatus.OK, payload);
    }

    private static JsonObject endpoint(String method, String path, boolean requiresKey, String description) {
        JsonObject object = new JsonObject();
        object.addProperty("method", method);
        object.addProperty("path", path);
        object.addProperty("requiresKey", requiresKey);
        object.addProperty("description", description);
        return object;
    }

    private void sendCommandList(ChannelHandlerContext ctx, FullHttpRequest req, CmsApiKey key) {
        CommandRegistry registry = registry();
        JsonArray commands = new JsonArray();
        if (registry != null) {
            List<String> keys = registry.getCommands();
            keys.sort(String::compareTo);
            for (String command : keys) {
                if (!CmsCommandScopes.isAlwaysDenied(command) && CmsCommandScopes.isAllowed(key.scopes(), command)) {
                    commands.add(command);
                }
            }
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("status", 0);
        payload.addProperty("message", "");
        payload.add("commands", commands);
        sendJson(ctx, req, HttpResponseStatus.OK, payload);
    }

    static HttpResponseStatus httpStatusFor(CommandResult result) {
        if (!result.known()) {
            return HttpResponseStatus.NOT_FOUND;
        }
        return switch (result.status()) {
            case 0 -> HttpResponseStatus.OK;
            case 2, 3 -> HttpResponseStatus.NOT_FOUND; // habbo / room not found
            case 4 -> HttpResponseStatus.INTERNAL_SERVER_ERROR;
            default -> HttpResponseStatus.BAD_REQUEST; // validation / domain error
        };
    }

    // --- auth + config plumbing -------------------------------------------------

    private static CmsApiAuthenticator authenticator() {
        CmsApiSettings settings = CmsApiSettings.get();
        String rawKeys = settings.getValue("cms.api.keys", "");
        long skew = Math.max(1, settings.getInt("cms.api.timestamp.skew.seconds", 300));
        long ttl = Math.max(skew, settings.getInt("cms.api.nonce.ttl.seconds", 600));

        AuthSnapshot current = AUTH_SNAPSHOT.get();
        if (current != null && current.rawKeys().equals(rawKeys) && current.skew() == skew && current.ttl() == ttl) {
            return current.authenticator();
        }
        CmsApiKeyStore store = CmsApiKeyStore.parse(rawKeys);
        CmsApiAuthenticator authenticator = new CmsApiAuthenticator(store, skew, ttl, Emulator::getIntUnixTimestamp);
        AUTH_SNAPSHOT.set(new AuthSnapshot(rawKeys, skew, ttl, authenticator));
        return authenticator;
    }

    private static CommandRegistry registry() {
        RCONServer rcon = Emulator.getRconServer();
        return rcon == null ? null : rcon.getRegistry();
    }

    static boolean isAddressAllowed(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        com.eu.habbo.core.ConfigurationManager config = Emulator.getConfig();
        String allowed = config != null ? config.getValue("cms.api.allowed", "127.0.0.1;::1") : "127.0.0.1;::1";
        byte[] address = null; // parsed lazily, only for CIDR entries
        for (String entry : allowed.split(";")) {
            String t = entry.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.indexOf('/') > 0) {
                CidrRange range = CidrRange.parse(t);
                if (range == null) {
                    continue;
                }
                if (address == null) {
                    address = CidrRange.parseAddress(ip);
                }
                if (range.contains(address)) {
                    return true;
                }
            } else if (ip.equals(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean acquirePermit(String ip) {
        if (!CmsApiSettings.get().getBoolean("cms.api.rate_limit.enabled", true)) {
            return true;
        }
        return RATE_LIMITERS.get(ip).acquirePermission();
    }

    private static RateLimiter newRateLimiter(String ip) {
        CmsApiSettings settings = CmsApiSettings.get();
        RateLimiterConfig limiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(Math.max(1, settings.getInt("cms.api.rate_limit.limit_for_period", 120)))
                .limitRefreshPeriod(
                        Duration.ofMillis(Math.max(100, settings.getInt("cms.api.rate_limit.refresh_period_ms", 1000))))
                .timeoutDuration(Duration.ZERO)
                .build();
        return RateLimiter.of("cms-api-" + ip, limiterConfig);
    }

    private int maxPayloadBytes() {
        int configured = CmsApiSettings.get().getInt("cms.api.max_payload_bytes", DEFAULT_MAX_PAYLOAD_BYTES);
        return configured > 0 ? configured : DEFAULT_MAX_PAYLOAD_BYTES;
    }

    private static com.eu.habbo.core.ConfigurationManager config() {
        return Emulator.getConfig();
    }

    // --- HTTP helpers -----------------------------------------------------------

    private static boolean isGet(FullHttpRequest req) {
        return req.method() == HttpMethod.GET || req.method() == HttpMethod.HEAD;
    }

    private static String header(FullHttpRequest req, String name) {
        String value = req.headers().get(name);
        return value == null ? "" : value;
    }

    private static String clientIp(ChannelHandlerContext ctx) {
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress addr && addr.getAddress() != null) {
            return addr.getAddress().getHostAddress();
        }
        return "";
    }

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    private static boolean isSecure(ChannelHandlerContext ctx) {
        return ctx.pipeline().get(SslHandler.class) != null;
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
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static boolean isKeepAlive(FullHttpRequest req) {
        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        return connection == null || !"close".equalsIgnoreCase(connection);
    }
}
