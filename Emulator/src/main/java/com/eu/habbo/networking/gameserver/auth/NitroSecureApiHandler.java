package com.eu.habbo.networking.gameserver.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class NitroSecureApiHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NitroSecureApiHandler.class);
    private static final String ENABLED_CONFIG = "nitro.secure.api.enabled";
    private static final String MAX_PAYLOAD_CONFIG = "nitro.secure.api.max_payload_bytes";
    private static final String API_PREFIX = "/api/";
    private static final int DEFAULT_MAX_PAYLOAD_BYTES = 64 * 1024;
    private static final AttributeKey<Deque<SecureApiContext>> SECURE_CONTEXTS =
            AttributeKey.valueOf("nitroSecureApiContexts");
    private static final ConcurrentHashMap<String, Long> NONCE_CACHE = new ConcurrentHashMap<>();
    private static final long MAX_REQUEST_SKEW_MS = 90_000L;
    private static final long NONCE_TTL_MS = 2 * 60 * 1000L;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();

        if (!secureApiEnabled()) {
            super.channelRead(ctx, msg);
            return;
        }

        if (!path.startsWith(API_PREFIX)) {
            super.channelRead(ctx, msg);
            return;
        }

        if (req.method() == HttpMethod.OPTIONS) {
            sendCors(ctx, req);
            ReferenceCountUtil.release(req);
            return;
        }

        if (!isSecureRequest(req)) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            String clientKey = req.headers().get("X-Nitro-Key");
            if (clientKey == null || clientKey.isBlank()) {
                sendText(ctx, req, HttpResponseStatus.UNAUTHORIZED, "Missing key.");
                return;
            }

            SecretKey sessionKey = NitroSecureAssetHandler.deriveSessionKey(java.util.Base64.getDecoder().decode(clientKey));
            SecureApiContext secureContext = new SecureApiContext(
                    NitroSecureAssetHandler.getServerKeyFingerprint(),
                    NitroSecureAssetHandler.fingerprint(sessionKey.getEncoded()),
                    sessionKey
            );

            if (!req.content().isReadable()) {
                enqueueContext(ctx, secureContext);
                super.channelRead(ctx, msg);
                return;
            }

            int readableBytes = req.content().readableBytes();
            int maxPayloadBytes = maxPayloadBytes();
            if (readableBytes > maxPayloadBytes) {
                sendText(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, "Secure payload too large.");
                return;
            }

            byte[] encrypted = new byte[readableBytes];
            req.content().getBytes(req.content().readerIndex(), encrypted);
            byte[] clear = NitroSecureAssetHandler.decrypt(sessionKey, NitroSecureAssetHandler.fromHex(new String(encrypted, StandardCharsets.UTF_8)));
            clear = unwrapEnvelope(clear, req, secureContext);

            FullHttpRequest decryptedReq = new DefaultFullHttpRequest(
                    req.protocolVersion(),
                    req.method(),
                    req.uri(),
                    Unpooled.wrappedBuffer(clear)
            );

            decryptedReq.headers().setAll(req.headers());
            decryptedReq.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
            decryptedReq.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, clear.length);

            enqueueContext(ctx, secureContext);
            ReferenceCountUtil.release(req);
            ctx.fireChannelRead(decryptedReq);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Nitro secure API rejected invalid encrypted payload", e);
            sendText(ctx, req, HttpResponseStatus.BAD_REQUEST, e.getMessage());
            ReferenceCountUtil.release(req);
        } catch (Exception e) {
            LOGGER.error("Nitro secure API failed to decrypt request", e);
            sendText(ctx, req, HttpResponseStatus.BAD_REQUEST, "Invalid secure payload.");
            ReferenceCountUtil.release(req);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof FullHttpResponse response)) {
            super.write(ctx, msg, promise);
            return;
        }

        SecureApiContext secureContext = pollContext(ctx);
        if (secureContext == null) {
            super.write(ctx, msg, promise);
            return;
        }

        try {
            byte[] clear = readBytes(response.content());
            byte[] encrypted = NitroSecureAssetHandler.encrypt(secureContext.sessionKey(), clear);
            byte[] hex = NitroSecureAssetHandler.toHex(encrypted).getBytes(StandardCharsets.UTF_8);

            FullHttpResponse encryptedResponse = new DefaultFullHttpResponse(
                    response.protocolVersion(),
                    response.status(),
                    Unpooled.wrappedBuffer(hex)
            );

            encryptedResponse.headers().setAll(response.headers());
            encryptedResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
            encryptedResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, hex.length);
            encryptedResponse.headers().set("X-Nitro-Sec", "1");
            encryptedResponse.headers().set("X-Nitro-Key-Fp", secureContext.serverKeyFingerprint());
            encryptedResponse.headers().set("X-Nitro-Derive-Fp", secureContext.derivedFingerprint());
            encryptedResponse.headers().set("Access-Control-Expose-Headers", "X-Nitro-Sec, X-Nitro-Key-Fp, X-Nitro-Derive-Fp");

            ReferenceCountUtil.release(response);
            super.write(ctx, encryptedResponse, promise);
        } catch (Exception e) {
            LOGGER.error("Nitro secure API failed to encrypt response", e);
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Deque<SecureApiContext> contexts = ctx.channel().attr(SECURE_CONTEXTS).get();
        if (contexts != null) contexts.clear();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Deque<SecureApiContext> contexts = ctx.channel().attr(SECURE_CONTEXTS).get();
        if (contexts != null) contexts.clear();
        super.exceptionCaught(ctx, cause);
    }

    private static boolean isSecureRequest(FullHttpRequest req) {
        return "1".equals(req.headers().get("X-Nitro-Api"));
    }

    private static boolean secureApiEnabled() {
        return com.eu.habbo.Emulator.getConfig().getBoolean(ENABLED_CONFIG, true);
    }

    static int maxPayloadBytes() {
        if (com.eu.habbo.Emulator.getConfig() == null) {
            return DEFAULT_MAX_PAYLOAD_BYTES;
        }

        int configured = com.eu.habbo.Emulator.getConfig().getInt(MAX_PAYLOAD_CONFIG, DEFAULT_MAX_PAYLOAD_BYTES);
        return configured > 0 ? configured : DEFAULT_MAX_PAYLOAD_BYTES;
    }

    private static byte[] unwrapEnvelope(byte[] clear, FullHttpRequest req, SecureApiContext secureContext) {
        if (!requiresReplayEnvelope(req.method())) return clear;

        JsonObject envelope = JsonParser.parseString(new String(clear, StandardCharsets.UTF_8)).getAsJsonObject();
        long ts = envelope.has("ts") ? envelope.get("ts").getAsLong() : 0L;
        String nonce = envelope.has("nonce") ? envelope.get("nonce").getAsString() : "";
        String method = envelope.has("method") ? envelope.get("method").getAsString() : "";
        String path = envelope.has("path") ? envelope.get("path").getAsString() : "";
        String body = envelope.has("body") ? envelope.get("body").getAsString() : "";
        long now = System.currentTimeMillis();

        if (Math.abs(now - ts) > MAX_REQUEST_SKEW_MS) {
            throw new IllegalArgumentException("Secure request expired.");
        }

        if (!req.method().name().equalsIgnoreCase(method)) {
            throw new IllegalArgumentException("Secure request method mismatch.");
        }

        String requestPath = req.uri();
        if (!requestPath.equals(path)) {
            throw new IllegalArgumentException("Secure request path mismatch.");
        }

        if (nonce.isBlank()) {
            throw new IllegalArgumentException("Missing secure request nonce.");
        }

        cleanupExpiredNonces(now);

        String replayKey = secureContext.derivedFingerprint() + ':' + nonce;
        if (NONCE_CACHE.putIfAbsent(replayKey, now + NONCE_TTL_MS) != null) {
            throw new IllegalArgumentException("Secure request replay detected.");
        }

        return java.util.Base64.getDecoder().decode(body);
    }

    private static boolean requiresReplayEnvelope(HttpMethod method) {
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH
                || method == HttpMethod.DELETE;
    }

    private static void cleanupExpiredNonces(long now) {
        if (NONCE_CACHE.size() < 512) return;
        NONCE_CACHE.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private static void enqueueContext(ChannelHandlerContext ctx, SecureApiContext context) {
        Deque<SecureApiContext> queue = ctx.channel().attr(SECURE_CONTEXTS).get();
        if (queue == null) {
            queue = new ArrayDeque<>();
            ctx.channel().attr(SECURE_CONTEXTS).set(queue);
        }

        queue.addLast(context);
    }

    private static SecureApiContext pollContext(ChannelHandlerContext ctx) {
        Deque<SecureApiContext> queue = ctx.channel().attr(SECURE_CONTEXTS).get();
        if (queue == null || queue.isEmpty()) return null;
        return queue.pollFirst();
    }

    private static byte[] readBytes(ByteBuf content) {
        byte[] bytes = new byte[content.readableBytes()];
        content.getBytes(content.readerIndex(), bytes);
        return bytes;
    }

    private static void sendCors(ChannelHandlerContext ctx, FullHttpRequest req) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendText(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    private static void applyCors(FullHttpRequest req, FullHttpResponse response) {
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);

        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Credentials", "true");
        }
        response.headers().set("Access-Control-Allow-Methods", "GET, HEAD, POST, OPTIONS");

        String requestedHeaders = req.headers().get("Access-Control-Request-Headers");
        if (requestedHeaders != null && !requestedHeaders.isEmpty()) {
            response.headers().set("Access-Control-Allow-Headers", requestedHeaders);
        } else {
            response.headers().set("Access-Control-Allow-Headers",
                    "Authorization, Content-Type, X-Requested-With, X-Nitro-Key, X-Nitro-Api");
        }

        response.headers().set("Vary", "Origin, Access-Control-Request-Headers, Access-Control-Request-Method");
        response.headers().set("Access-Control-Max-Age", "600");
        response.headers().set("Access-Control-Expose-Headers", "X-Nitro-Sec, X-Nitro-Key-Fp, X-Nitro-Derive-Fp");
    }

    private static boolean isKeepAlive(FullHttpRequest req) {
        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        return connection == null || !"close".equalsIgnoreCase(connection);
    }

    private record SecureApiContext(String serverKeyFingerprint, String derivedFingerprint, SecretKey sessionKey) {
        private SecureApiContext {
            Objects.requireNonNull(serverKeyFingerprint);
            Objects.requireNonNull(derivedFingerprint);
            Objects.requireNonNull(sessionKey);
        }
    }
}
