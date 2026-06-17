package com.eu.habbo.networking.gameserver.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.MAX_BODY_BYTES;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.errorPayload;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.readString;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.resolveClientIp;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.sendCors;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.sendJson;

public class AuthHttpHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHttpHandler.class);

    // Dedicated, bounded pool for the auth endpoints. Their work blocks on
    // BCrypt, JDBC, the Turnstile HTTPS round-trip and SMTP — running that on the
    // Netty event loop stalls every client on the same worker. A SEPARATE pool
    // (not the shared game ThreadPooling) also keeps it from starving room cycles.
    private static final int AUTH_POOL_MAX = authPoolMax();
    private static final ThreadPoolExecutor AUTH_EXECUTOR = new ThreadPoolExecutor(
            Math.min(4, AUTH_POOL_MAX), AUTH_POOL_MAX, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(512),
            new java.util.concurrent.ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "auth-http-worker-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

    // Max threads for the auth pool. Defaults to 16; set the optional
    // `auth.http.pool.size` config key to override.
    private static int authPoolMax() {
        int fallback = 16;
        if (com.eu.habbo.Emulator.getConfig() == null) {
            return fallback;
        }
        int configured = com.eu.habbo.Emulator.getConfig().getInt("auth.http.pool.size", fallback);
        return configured > 0 ? configured : fallback;
    }

    static final String LOGIN_PATH           = "/api/auth/login";
    static final String REGISTER_PATH        = "/api/auth/register";
    static final String FORGOT_PATH          = "/api/auth/forgot-password";
    static final String LOGOUT_PATH          = "/api/auth/logout";
    static final String CHECK_EMAIL_PATH     = "/api/auth/check-email";
    static final String CHECK_USERNAME_PATH  = "/api/auth/check-username";
    static final String ROOM_TEMPLATES_PATH  = "/api/auth/room-templates";
    static final String NEWS_PATH            = "/api/auth/news";
    static final String REMEMBER_PATH        = "/api/auth/remember";
    static final String REFRESH_PATH         = "/api/auth/refresh";
    static final String SERVER_KEY_PATH      = "/api/auth/server-key";
    static final String SSO_TOKEN_PATH       = "/api/auth/sso-token";
    static final String CHANGE_PASSWORD_PATH = "/api/auth/change-password";
    static final String CHANGE_EMAIL_PATH    = "/api/auth/change-email";
    static final String CHANGE_USERNAME_PATH = "/api/auth/change-username";
    static final String HEALTH_PATH          = "/api/health";

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

        // Offload the (potentially blocking) auth work off the event loop. Netty
        // writes are thread-safe, so the endpoints' sendJson/writeAndFlush calls
        // are fine from the worker; the request is released once the work ends.
        try {
            AUTH_EXECUTOR.execute(() -> {
                try {
                    handle(ctx, req, path);
                } catch (Throwable t) {
                    LOGGER.error("Auth handler failed for {}", path, t);
                    try {
                        sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Internal error."));
                    } catch (Throwable ignored) {
                        // response may already be partially written — nothing else to do
                    }
                } finally {
                    ReferenceCountUtil.release(req);
                }
            });
        } catch (RejectedExecutionException rejected) {
            try {
                sendJson(ctx, req, HttpResponseStatus.SERVICE_UNAVAILABLE, errorPayload("Server busy, try again shortly."));
            } finally {
                ReferenceCountUtil.release(req);
            }
        }
    }

    private static boolean isOurRoute(String path) {
        return path.equals(LOGIN_PATH)
                || path.equals(REGISTER_PATH)
                || path.equals(FORGOT_PATH)
                || path.equals(LOGOUT_PATH)
                || path.equals(CHECK_EMAIL_PATH)
                || path.equals(CHECK_USERNAME_PATH)
                || path.equals(ROOM_TEMPLATES_PATH)
                || path.equals(NEWS_PATH)
                || path.equals(REMEMBER_PATH)
                || path.equals(REFRESH_PATH)
                || path.equals(SERVER_KEY_PATH)
                || path.equals(SSO_TOKEN_PATH)
                || path.equals(CHANGE_PASSWORD_PATH)
                || path.equals(CHANGE_EMAIL_PATH)
                || path.equals(CHANGE_USERNAME_PATH)
                || path.equals(HEALTH_PATH);
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        if (req.method() == HttpMethod.OPTIONS) {
            sendCors(ctx, req);
            return;
        }

        if (path.equals(HEALTH_PATH)) {
            if (!isGetOrHead(req)) {
                sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, errorPayload("Use GET."));
                return;
            }
            JsonObject ok = new JsonObject();
            ok.addProperty("status", "ok");
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
            return;
        }

        if (path.equals(ROOM_TEMPLATES_PATH)) {
            if (!isGetOrHead(req)) {
                sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, errorPayload("Use GET."));
                return;
            }
            StaticContentEndpoints.handleRoomTemplates(ctx, req);
            return;
        }

        if (path.equals(NEWS_PATH)) {
            if (!isGetOrHead(req)) {
                sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, errorPayload("Use GET."));
                return;
            }
            String ip = resolveClientIp(ctx, req);
            if (!AuthRateLimiter.tryProbe(ip)) {
                long secs = AuthRateLimiter.secondsUntilProbeReset(ip);
                sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS,
                        errorPayload("Too many requests. Try again in " + secs + "s."));
                return;
            }
            StaticContentEndpoints.handleNews(ctx, req);
            return;
        }

        if (path.equals(SERVER_KEY_PATH)) {
            if (!isGetOrHead(req)) {
                sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, errorPayload("Use GET."));
                return;
            }
            StaticContentEndpoints.handleServerKey(ctx, req);
            return;
        }

        if (req.method() != HttpMethod.POST) {
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, errorPayload("Use POST."));
            return;
        }

        String ip = resolveClientIp(ctx, req);

        if (AuthRateLimiter.isLocked(ip)) {
            long secs = AuthRateLimiter.secondsUntilUnlock(ip);
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS,
                    errorPayload("Too many attempts. Try again in " + secs + "s."));
            return;
        }

        if (req.content().readableBytes() > MAX_BODY_BYTES) {
            sendJson(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, errorPayload("Payload too large."));
            return;
        }

        JsonObject body;
        try {
            String text = req.content().toString(StandardCharsets.UTF_8);
            body = text.isEmpty() ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Invalid JSON body."));
            return;
        }

        if (path.equals(LOGOUT_PATH))          { SessionEndpoints.handleLogout(ctx, req, body);             return; }
        if (path.equals(CHECK_EMAIL_PATH))     { AccountCheckEndpoints.handleCheckEmail(ctx, req, body, ip); return; }
        if (path.equals(CHECK_USERNAME_PATH))  { AccountCheckEndpoints.handleCheckUsername(ctx, req, body, ip); return; }
        if (path.equals(REMEMBER_PATH))        { SessionEndpoints.handleRemember(ctx, req, body, ip);       return; }
        if (path.equals(REFRESH_PATH))         { SessionEndpoints.handleRefresh(ctx, req, body, ip);        return; }
        if (path.equals(SSO_TOKEN_PATH))       { SessionEndpoints.handleSsoToken(ctx, req, body, ip);       return; }
        if (path.equals(CHANGE_PASSWORD_PATH)) { AccountChangeEndpoints.handleChangePassword(ctx, req, body, ip); return; }
        if (path.equals(CHANGE_EMAIL_PATH))    { AccountChangeEndpoints.handleChangeEmail(ctx, req, body, ip);    return; }
        if (path.equals(CHANGE_USERNAME_PATH)) { AccountChangeEndpoints.handleChangeUsername(ctx, req, body, ip); return; }

        String turnstileToken = readString(body, "turnstileToken");
        if (!TurnstileVerifier.verify(turnstileToken, ip)) {
            AuthRateLimiter.recordFailure(ip);
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, errorPayload("Security check failed."));
            return;
        }

        switch (path) {
            case LOGIN_PATH    -> SessionEndpoints.handleLogin(ctx, req, body, ip);
            case REGISTER_PATH -> SessionEndpoints.handleRegister(ctx, req, body, ip);
            case FORGOT_PATH   -> SessionEndpoints.handleForgot(ctx, req, body, ip);
        }
    }

    private static boolean isGetOrHead(FullHttpRequest req) {
        return req.method() == HttpMethod.GET || req.method() == HttpMethod.HEAD;
    }
}
