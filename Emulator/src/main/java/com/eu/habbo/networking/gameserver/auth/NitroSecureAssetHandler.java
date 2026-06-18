package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
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

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NitroSecureAssetHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(NitroSecureAssetHandler.class);
    private static final String MASTER_KEY_CONFIG = "nitro.secure.master_key";
    private static final String ENABLED_CONFIG = "nitro.secure.assets.enabled";
    private static final String BOOTSTRAP_PATH = "/nitro-sec/bootstrap";
    private static final String FILE_PATH = "/nitro-sec/file";
    private static final int MAX_BOOTSTRAP_BODY_BYTES = 4096;
    private static final int DEFAULT_MAX_CONFIG_BYTES = 2 * 1024 * 1024;
    private static final int DEFAULT_MAX_GAMEDATA_BYTES = 16 * 1024 * 1024;
    private static final SecureRandom RNG = new SecureRandom();
    private static final KeyPair SERVER_KEYPAIR = createServerKeyPair();
    private static final String SERVER_KEY_FINGERPRINT = fingerprint(SERVER_KEYPAIR.getPublic().getEncoded());
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();

        if (!secureAssetsEnabled()) {
            super.channelRead(ctx, msg);
            return;
        }

        if (!path.equals(BOOTSTRAP_PATH) && !path.equals(FILE_PATH)) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            if (req.method() == HttpMethod.OPTIONS) {
                sendCors(ctx, req);
                return;
            }

            if (path.equals(BOOTSTRAP_PATH)) handleBootstrap(ctx, req);
            else handleFile(ctx, req);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handleBootstrap(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() != HttpMethod.POST) {
            sendText(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, "Use POST.", "text/plain; charset=utf-8");
            return;
        }

        if (req.content().readableBytes() > MAX_BOOTSTRAP_BODY_BYTES) {
            sendText(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, "Payload too large.", "text/plain; charset=utf-8");
            return;
        }

        try {
            JsonObject body = JsonParser.parseString(req.content().toString(StandardCharsets.UTF_8)).getAsJsonObject();
            String clientKey = body.has("key") ? body.get("key").getAsString() : "";
            if (clientKey.isEmpty()) {
                sendText(ctx, req, HttpResponseStatus.BAD_REQUEST, "Missing key.", "text/plain; charset=utf-8");
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("key", Base64.getEncoder().encodeToString(SERVER_KEYPAIR.getPublic().getEncoded()));
            sendText(ctx, req, HttpResponseStatus.OK, response.toString(), "application/json; charset=utf-8");
        } catch (Exception e) {
            LOGGER.warn("Nitro secure bootstrap failed", e);
            sendText(ctx, req, HttpResponseStatus.BAD_REQUEST, "Invalid bootstrap.", "text/plain; charset=utf-8");
        }
    }

    private void handleFile(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() != HttpMethod.GET && req.method() != HttpMethod.HEAD) {
            sendText(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, "Use GET.", "text/plain; charset=utf-8");
            return;
        }

        QueryStringDecoder query = new QueryStringDecoder(req.uri());
        String clientKey = headerOrQuery(req, query, "X-Nitro-Key", "key");
        if (clientKey == null || clientKey.isEmpty()) {
            sendText(ctx, req, HttpResponseStatus.UNAUTHORIZED, "Missing key.", "text/plain; charset=utf-8");
            return;
        }

        String kind = queryParam(query, "kind");
        String file = queryParam(query, "file");
        if (!kind.equals("config") && !kind.equals("gamedata")) {
            sendText(ctx, req, HttpResponseStatus.BAD_REQUEST, "Invalid kind.", "text/plain; charset=utf-8");
            return;
        }

        try {
            SecretKey sessionKey = deriveSessionKey(Base64.getDecoder().decode(clientKey));
            byte[] clear = readAsset(kind, file);
            byte[] encrypted = encrypt(sessionKey, clear);
            sendText(ctx, req, HttpResponseStatus.OK, toHex(encrypted), "text/plain; charset=utf-8", true, fingerprint(sessionKey.getEncoded()));
        } catch (IllegalArgumentException e) {
            sendText(ctx, req, HttpResponseStatus.BAD_REQUEST, e.getMessage(), "text/plain; charset=utf-8");
        } catch (IOException e) {
            sendText(ctx, req, HttpResponseStatus.NOT_FOUND, "Not found.", "text/plain; charset=utf-8");
        } catch (Exception e) {
            LOGGER.error("Nitro secure asset failed kind=" + kind + " file=" + file, e);
            sendText(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Server error.", "text/plain; charset=utf-8");
        }
    }

    private static byte[] readAsset(String kind, String file) throws IOException {
        String normalized = normalizeFile(file);
        String rootConfigKey = kind.equals("config") ? "nitro.secure.config.root" : "nitro.secure.gamedata.root";
        String fallback = kind.equals("config") ? "Nitro-V3/public" : "Nitro-V3/public/nitro/gamedata";
        Path root = resolveRoot(rootConfigKey, fallback, kind.equals("config")
                ? new String[] { "../Nitro-V3/public", "../../Nitro-V3/public", "Nitro-V3/public" }
                : new String[] { "../Nitro-V3/public/nitro/gamedata", "../../Nitro-V3/public/nitro/gamedata", "Nitro-V3/public/nitro/gamedata" });
        Path target = root.resolve(normalized).normalize();

        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid file.");
        if (!Files.isRegularFile(target)) throw new IOException("Not found");
        long size = Files.size(target);
        int maxBytes = maxAssetBytes(kind);
        if (size > maxBytes) throw new IllegalArgumentException("File too large.");

        String cacheKey = kind + ":" + target;
        long modified = Files.getLastModifiedTime(target).toMillis();
        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && cached.modified == modified) return cached.bytes;

        byte[] bytes = Files.readAllBytes(target);
        if (normalized.toLowerCase().endsWith(".json")) bytes = minifyJson(bytes);
        CACHE.put(cacheKey, new CacheEntry(modified, bytes));
        return bytes;
    }

    static int maxAssetBytes(String kind) {
        boolean config = "config".equals(kind);
        String key = config ? "nitro.secure.config.max_file_bytes" : "nitro.secure.gamedata.max_file_bytes";
        int fallback = config ? DEFAULT_MAX_CONFIG_BYTES : DEFAULT_MAX_GAMEDATA_BYTES;
        int configured = Emulator.getConfig().getInt(key, fallback);
        return configured > 0 ? configured : fallback;
    }

    private static String normalizeFile(String file) {
        if (file == null) throw new IllegalArgumentException("Missing file.");
        String value = URLDecoder.decode(file, StandardCharsets.UTF_8).replace('\\', '/');
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) value = value.substring(0, queryIndex);
        int fragmentIndex = value.indexOf('#');
        if (fragmentIndex >= 0) value = value.substring(0, fragmentIndex);
        while (value.startsWith("/")) value = value.substring(1);
        if (value.isEmpty() || value.contains("..") || value.contains(":")) throw new IllegalArgumentException("Invalid file.");
        return value;
    }

    private static byte[] minifyJson(byte[] bytes) {
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return bytes;
        }
    }

    private static Path resolveRoot(String configKey, String fallback, String[] alternatives) {
        String configured = Emulator.getConfig().getValue(configKey, "");
        if (configured != null && !configured.isEmpty()) return Path.of(configured).toAbsolutePath().normalize();

        for (String alternative : alternatives) {
            Path path = Path.of(alternative).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) return path;
        }

        return Path.of(fallback).toAbsolutePath().normalize();
    }

    private static boolean secureAssetsEnabled() {
        return Emulator.getConfig().getBoolean(ENABLED_CONFIG, true);
    }

    static SecretKey deriveSessionKey(byte[] clientPublicEncoded) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("EC");
        PublicKey clientPublic = factory.generatePublic(new X509EncodedKeySpec(clientPublicEncoded));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(SERVER_KEYPAIR.getPrivate());
        agreement.doPhase(clientPublic, true);
        byte[] secret = agreement.generateSecret();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(secret);
        digest.update("nitro-secure-assets-v1".getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest.digest(), "AES");
    }

    static byte[] encrypt(SecretKey key, byte[] clear) throws Exception {
        byte[] iv = new byte[12];
        RNG.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(clear);
        byte[] out = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
        return out;
    }

    static byte[] decrypt(SecretKey key, byte[] encryptedPayload) throws Exception {
        if (encryptedPayload.length < 13) throw new IllegalArgumentException("Encrypted payload is too short.");
        byte[] iv = new byte[12];
        byte[] payload = new byte[encryptedPayload.length - iv.length];
        System.arraycopy(encryptedPayload, 0, iv, 0, iv.length);
        System.arraycopy(encryptedPayload, iv.length, payload, 0, payload.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return cipher.doFinal(payload);
    }

    private static KeyPair createServerKeyPair() {
        try {
            String configuredSecret = Emulator.getConfig().getValue(MASTER_KEY_CONFIG, "");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            if (configuredSecret != null && !configuredSecret.isBlank()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] seed = digest.digest(configuredSecret.getBytes(StandardCharsets.UTF_8));
                SecureRandom deterministic = SecureRandom.getInstance("SHA1PRNG");
                deterministic.setSeed(seed);
                generator.initialize(256, deterministic);
                LOGGER.info("Nitro secure assets using persistent server key from config {}", MASTER_KEY_CONFIG);
            } else {
                generator.initialize(256, RNG);
                LOGGER.warn("Nitro secure assets using ephemeral server key because {} is empty", MASTER_KEY_CONFIG);
            }
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create Nitro secure server key", e);
        }
    }

    private static String headerOrQuery(FullHttpRequest req, QueryStringDecoder query, String header, String param) {
        String value = req.headers().get(header);
        return (value == null || value.isEmpty()) ? queryParam(query, param) : value;
    }

    private static String queryParam(QueryStringDecoder query, String key) {
        if (!query.parameters().containsKey(key) || query.parameters().get(key).isEmpty()) return "";
        return query.parameters().get(key).get(0);
    }

    private static void sendText(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String text, String contentType) {
        sendBytes(ctx, req, status, text.getBytes(StandardCharsets.UTF_8), contentType, false, null);
    }

    private static void sendText(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String text, String contentType, boolean encrypted, String deriveFingerprint) {
        sendBytes(ctx, req, status, text.getBytes(StandardCharsets.UTF_8), contentType, encrypted, deriveFingerprint);
    }

    private static void sendBytes(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, byte[] bytes, String contentType, boolean encrypted) {
        sendBytes(ctx, req, status, bytes, contentType, encrypted, null);
    }

    private static void sendBytes(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, byte[] bytes, String contentType, boolean encrypted, String deriveFingerprint) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        if (encrypted) response.headers().set("X-Nitro-Sec", "1");
        response.headers().set("X-Nitro-Key-Fp", SERVER_KEY_FINGERPRINT);
        if (deriveFingerprint != null && !deriveFingerprint.isEmpty()) response.headers().set("X-Nitro-Derive-Fp", deriveFingerprint);
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendCors(ChannelHandlerContext ctx, FullHttpRequest req) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void applyCors(FullHttpRequest req, FullHttpResponse response) {
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);

        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set("Access-Control-Allow-Origin", origin);
        }
        response.headers().set("Access-Control-Allow-Methods", "GET, HEAD, POST, OPTIONS");

        String requestedHeaders = req.headers().get("Access-Control-Request-Headers");
        if (requestedHeaders != null && !requestedHeaders.isEmpty()) {
            response.headers().set("Access-Control-Allow-Headers", requestedHeaders);
        } else {
            response.headers().set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Nitro-Key");
        }

        response.headers().set("Vary", "Origin, Access-Control-Request-Headers, Access-Control-Request-Method");
        response.headers().set("Access-Control-Max-Age", "600");
        response.headers().set("Access-Control-Expose-Headers", "X-Nitro-Sec, X-Nitro-Key-Fp, X-Nitro-Derive-Fp");
    }

    private static boolean isKeepAlive(FullHttpRequest req) {
        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        return connection == null || !"close".equalsIgnoreCase(connection);
    }

    static String fingerprint(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    static String getServerKeyFingerprint() {
        return SERVER_KEY_FINGERPRINT;
    }

    static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    static byte[] fromHex(String hex) {
        String normalized = hex == null ? "" : hex.trim();
        if ((normalized.length() % 2) != 0) throw new IllegalArgumentException("Invalid encrypted hex payload.");

        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(normalized.charAt(i * 2), 16);
            int low = Character.digit(normalized.charAt((i * 2) + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid encrypted hex payload.");
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    private record CacheEntry(long modified, byte[] bytes) {}
}
