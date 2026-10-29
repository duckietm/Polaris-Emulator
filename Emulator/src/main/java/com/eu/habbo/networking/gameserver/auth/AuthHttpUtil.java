package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import com.google.gson.JsonElement;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuthHttpUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHttpUtil.class);

    static final Pattern USERNAME_RE = Pattern.compile("^[A-Za-z0-9._-]{3,32}$");
    static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    static final Pattern FIGURE_RE = Pattern.compile("^[A-Za-z0-9.\\-]{1,200}$");

    static final SecureRandom RNG = new SecureRandom();
    static final int MAX_BODY_BYTES = 8 * 1024;

    private static final long PERMANENT_BAN_THRESHOLD_SECONDS = 30L * 365L * 24L * 60L * 60L;

    private AuthHttpUtil() {}

    static String readString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    static int readInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return defaultValue;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static boolean readBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return defaultValue;
        try {
            JsonElement el = obj.get(key);
            if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
            String s = el.getAsString();
            return "1".equals(s) || "true".equalsIgnoreCase(s);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static JsonObject errorPayload(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        return obj;
    }

    static void sendJson(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, JsonObject body) {
        sendJson(ctx, req, status, body, null);
    }

    /** As {@link #sendJson(ChannelHandlerContext, FullHttpRequest, HttpResponseStatus, JsonObject)}, with a cookie. */
    static void sendJson(
            ChannelHandlerContext ctx,
            FullHttpRequest req,
            HttpResponseStatus status,
            JsonObject body,
            String setCookie) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        if (setCookie != null) response.headers().add(HttpHeaderNames.SET_COOKIE, setCookie);
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    static void sendCors(ChannelHandlerContext ctx, FullHttpRequest req) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    static void applyCors(FullHttpRequest req, FullHttpResponse response) {
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
            response.headers()
                    .set(
                            "Access-Control-Allow-Headers",
                            "Authorization, Content-Type, X-Requested-With, X-Nitro-Key, X-Nitro-Api");
        }

        response.headers().set("Vary", "Origin, Access-Control-Request-Headers, Access-Control-Request-Method");
        response.headers().set("Access-Control-Max-Age", "600");
        response.headers().set("Access-Control-Expose-Headers", "X-Nitro-Sec, X-Nitro-Key-Fp, X-Nitro-Derive-Fp");
    }

    static boolean isKeepAlive(FullHttpRequest req) {
        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        return connection == null || !"close".equalsIgnoreCase(connection);
    }

    static String resolveClientIp(ChannelHandlerContext ctx, FullHttpRequest req) {
        String ipHeader = Emulator.getConfig() != null ? Emulator.getConfig().getValue("ws.ip.header", "") : "";
        // Only trust a client-supplied forwarded-IP header when the DIRECT peer
        // is a trusted reverse proxy; otherwise an attacker hitting the port
        // directly could spoof it to evade per-IP rate limiting and IP bans.
        if (!ipHeader.isEmpty() && req.headers().contains(ipHeader) && shouldHonorForwardedHeader(ctx, ipHeader)) {
            String forwarded = resolveForwardedClientIp(req.headers().get(ipHeader));
            if (!forwarded.isEmpty()) {
                return forwarded;
            }
        }
        if (ctx.channel().attr(GameServerAttributes.WS_IP).get() != null) {
            return ctx.channel().attr(GameServerAttributes.WS_IP).get();
        }
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress addr) {
            return addr.getAddress().getHostAddress();
        }
        return "";
    }

    /**
     * Gate for honouring a forwarded-IP header, with operator feedback: when a
     * request carries the configured header but the direct peer is not a
     * trusted proxy, log a one-time warning naming the peer so "everyone has
     * the proxy IP" is diagnosable instead of silent.
     */
    public static boolean shouldHonorForwardedHeader(ChannelHandlerContext ctx, String headerName) {
        if (isTrustedProxy(ctx)) return true;

        String peerIp = (ctx.channel().remoteAddress() instanceof InetSocketAddress a)
                ? a.getAddress().getHostAddress()
                : null;
        if (peerIp != null && WARNED_UNTRUSTED_PEERS.size() < MAX_WARNED_PEERS && WARNED_UNTRUSTED_PEERS.add(peerIp)) {
            LOGGER.warn(
                    "Ignoring forwarded-IP header '{}' from untrusted peer {} — if this is your reverse proxy, "
                            + "add its IP to the ws.ip.header.trusted setting (comma-separated; a trailing '.' or ':' makes it a prefix range).",
                    headerName,
                    peerIp);
        }
        return false;
    }

    private static final int MAX_WARNED_PEERS = 64;
    private static final java.util.Set<String> WARNED_UNTRUSTED_PEERS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * The client IP carried by a forwarded-IP header, read from right to left.
     *
     * <p>Proxies <em>append</em>: nginx's {@code $proxy_add_x_forwarded_for} and
     * Cloudflare both put the peer they actually saw at the END of the chain, so
     * the leftmost hop is whatever the client sent and is fully attacker
     * controlled. Walking from the right and skipping our own trusted proxies
     * yields the furthest hop we can still vouch for. A hop that is not an IP
     * literal means the chain cannot be read, so the whole header is discarded
     * and the caller falls back to the socket peer.
     *
     * @param headerValue the raw header value, possibly {@code null}
     * @return the client IP, or an empty string when the header cannot be trusted
     */
    public static String resolveForwardedClientIp(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) return "";
        String[] hops = headerValue.split(",", -1);
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = normalizeForwardedHop(hops[i]);
            if (hop.isEmpty()) continue; // an empty hop carries no claim either way
            if (CidrRange.parseAddress(hop) == null) return "";
            if (isTrustedProxyIp(hop)) continue;
            return hop;
        }
        return "";
    }

    /** Strips the quoting, brackets and port a proxy may add to one hop. */
    private static String normalizeForwardedHop(String raw) {
        String hop = raw == null ? "" : raw.trim();
        if (hop.length() >= 2 && hop.charAt(0) == '"' && hop.endsWith("\"")) {
            hop = hop.substring(1, hop.length() - 1).trim();
        }
        if (hop.startsWith("[")) { // [2001:db8::1] or [2001:db8::1]:443
            int end = hop.indexOf(']');
            return end > 1 ? hop.substring(1, end) : "";
        }
        int colon = hop.indexOf(':');
        if (colon > 0 && hop.indexOf(':', colon + 1) < 0) { // a single colon means IPv4:port
            hop = hop.substring(0, colon);
        }
        return hop;
    }

    /**
     * Whether the channel's direct peer may set a forwarded-IP header. Loopback
     * is always trusted; Cloudflare's published edge ranges are trusted when
     * {@code ws.ip.header} is {@code CF-Connecting-IP} (see
     * {@link CloudflareIpRanges}); additional proxies can be allow-listed via
     * the {@code ws.ip.header.trusted} config key (comma-separated exact IPs,
     * dotted/colon prefixes, or CIDR blocks like {@code 10.0.0.0/8}).
     * Default-deny so the header can't be spoofed from the open net.
     */
    public static boolean isTrustedProxy(ChannelHandlerContext ctx) {
        String peerIp = (ctx.channel().remoteAddress() instanceof InetSocketAddress a)
                ? a.getAddress().getHostAddress()
                : null;
        return isTrustedProxyIp(peerIp);
    }

    /** {@link #isTrustedProxy} for an IP that is not the socket peer — a hop inside the header. */
    public static boolean isTrustedProxyIp(String peerIp) {
        if (peerIp == null || peerIp.isEmpty()) return false;
        if (peerIp.equals("127.0.0.1") || peerIp.equals("::1") || peerIp.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        if (CloudflareIpRanges.isCloudflareEdge(peerIp)) return true;
        String trusted = Emulator.getConfig() != null ? Emulator.getConfig().getValue("ws.ip.header.trusted", "") : "";
        if (trusted.isEmpty()) return false;
        byte[] peerAddress = null; // lazily parsed, only needed for CIDR entries
        for (String entry : trusted.split(",")) {
            String t = entry.trim();
            if (t.isEmpty()) continue;
            // CIDR block, e.g. "10.0.0.0/8" or "2400:cb00::/32".
            if (t.indexOf('/') > 0) {
                CidrRange range = CidrRange.parse(t);
                if (range == null) continue;
                if (peerAddress == null) peerAddress = CidrRange.parseAddress(peerIp);
                if (range.contains(peerAddress)) return true;
                continue;
            }
            // Exact IP match, or a dotted/colon prefix range (e.g. "10.0.0." or
            // "2001:db8:") — never a bare-IP prefix, so "10.0.0.1" can't also
            // trust "10.0.0.12".
            boolean isRange = t.endsWith(".") || t.endsWith(":");
            if (peerIp.equals(t) || (isRange && peerIp.startsWith(t))) {
                return true;
            }
        }
        return false;
    }

    static boolean checkPassword(String plain, String stored) {
        return PasswordHasher.verify(plain, stored);
    }

    static String mintSsoTicket() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return "nitro-" + Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    static String mintResetToken() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    static final class BanInfo {
        final String type;
        final String reason;
        final int expiresAt;

        BanInfo(String type, String reason, int expiresAt) {
            this.type = type == null ? "account" : type;
            this.reason = reason == null ? "" : reason;
            this.expiresAt = expiresAt;
        }

        boolean isPermanent() {
            return (long) expiresAt - Emulator.getIntUnixTimestamp() > PERMANENT_BAN_THRESHOLD_SECONDS;
        }
    }

    static BanInfo lookupAccountBan(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT ban_expire, ban_reason, type FROM bans "
                + "WHERE user_id = ? AND ban_expire >= ? AND (type = 'account' OR type = 'super') "
                + "ORDER BY ban_expire DESC LIMIT 1")) {
            stmt.setInt(1, userId);
            stmt.setInt(2, Emulator.getIntUnixTimestamp());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new BanInfo(rs.getString("type"), rs.getString("ban_reason"), rs.getInt("ban_expire"));
                }
            }
        }
        return null;
    }

    static BanInfo lookupIpBan(Connection conn, String ip) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT ban_expire, ban_reason, type FROM bans "
                + "WHERE ip = ? AND ban_expire >= ? AND (type = 'ip' OR type = 'super') "
                + "ORDER BY ban_expire DESC LIMIT 1")) {
            stmt.setString(1, ip);
            stmt.setInt(2, Emulator.getIntUnixTimestamp());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new BanInfo(rs.getString("type"), rs.getString("ban_reason"), rs.getInt("ban_expire"));
                }
            }
        }
        return null;
    }

    static JsonObject bannedPayload(BanInfo ban) {
        boolean permanent = ban.isPermanent();
        String message =
                permanent ? "Your account has been permanently banned." : "Your account is temporarily banned.";

        JsonObject details = new JsonObject();
        details.addProperty("type", ban.type);
        details.addProperty("reason", ban.reason);
        details.addProperty("permanent", permanent);
        if (!permanent) details.addProperty("expiresAt", ban.expiresAt);

        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        obj.add("ban", details);
        return obj;
    }
}
