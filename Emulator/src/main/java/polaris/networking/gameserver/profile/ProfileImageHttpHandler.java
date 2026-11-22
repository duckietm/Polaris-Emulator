package polaris.networking.gameserver.profile;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBufUtil;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.core.ConfigurationManager;
import polaris.hotel.users.profile.ProfileImageService;
import polaris.hotel.users.profile.ProfileImageService.Kind;
import polaris.hotel.users.profile.ProfileImageService.ProfileImageException;
import polaris.networking.gameserver.auth.AccessTokenService;
import polaris.networking.gameserver.auth.CorsOriginGate;

/**
 * {@code POST /profile-images/upload?kind=banner|background} stores the signed-in player's picture (the raw
 * JPEG or PNG as the body), {@code DELETE} on the same path removes it, and {@code GET
 * /profile-images/<file>} serves the stored pictures. The upload stays outside {@code /api/}: the client
 * encrypts every {@code /api/} call, and the secure envelope takes at most a few dozen kilobytes.
 */
public class ProfileImageHttpHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileImageHttpHandler.class);

    static final String API_PATH = "/profile-images/upload";
    static final String FILES_PATH = "/profile-images/";

    private final ProfileImageService service;

    public ProfileImageHttpHandler(ConfigurationManager configuration) {
        this(ProfileImageService.get());
        this.service.configure(ProfileImageService.Settings.from(configuration));
    }

    ProfileImageHttpHandler(ProfileImageService service) {
        this.service = service;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!path.startsWith(FILES_PATH)) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            if (path.equals(API_PATH)) handleApi(ctx, req);
            else serveFile(ctx, req, path.substring(FILES_PATH.length()));
        } catch (Exception e) {
            LOGGER.error("[profile/image] unexpected error path={}", path, e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, result(false, "failed"));
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handleApi(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() == HttpMethod.OPTIONS) {
            FullHttpResponse response =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
            applyCors(req, response);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, result(false, "unauthorized"));
            return;
        }

        List<String> kinds = new QueryStringDecoder(req.uri()).parameters().get("kind");
        Kind kind = kinds == null || kinds.isEmpty() ? null : Kind.fromKey(kinds.get(0));
        if (kind == null) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, result(false, "invalid"));
            return;
        }

        if (req.method() == HttpMethod.DELETE) {
            this.service.remove(userId, kind);
            sendJson(ctx, req, HttpResponseStatus.OK, result(true, "ok"));
            return;
        }

        if (req.method() != HttpMethod.POST) {
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, result(false, "invalid"));
            return;
        }

        if (req.content().readableBytes() > ProfileImageService.MAX_BYTES) {
            sendJson(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, result(false, "too_large"));
            return;
        }

        try {
            String file = this.service.store(userId, kind, ByteBufUtil.getBytes(req.content()));
            JsonObject body = result(true, "ok");
            body.addProperty("file", file);
            sendJson(ctx, req, HttpResponseStatus.OK, body);
        } catch (ProfileImageException e) {
            sendJson(ctx, req, statusFor(e.getCode()), result(false, e.getCode()));
        }
    }

    private void serveFile(ChannelHandlerContext ctx, FullHttpRequest req, String fileName) throws IOException {
        if (req.method() != HttpMethod.GET && req.method() != HttpMethod.HEAD) {
            send(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED));
            return;
        }

        Path file = this.service.resolve(fileName);
        if (file == null) {
            send(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND));
            return;
        }

        byte[] bytes = req.method() == HttpMethod.HEAD ? new byte[0] : Files.readAllBytes(file);
        FullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/jpeg");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, (int) Files.size(file));
        // A file name is never reused: a new picture always gets a new name.
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=31536000, immutable");
        response.headers().set("X-Content-Type-Options", "nosniff");
        response.headers().set("Access-Control-Allow-Origin", "*");
        send(ctx, req, response);
    }

    static HttpResponseStatus statusFor(String code) {
        return switch (code) {
            case "too_large" -> HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
            case "rate_limited" -> HttpResponseStatus.TOO_MANY_REQUESTS;
            case "blocked", "disabled" -> HttpResponseStatus.FORBIDDEN;
            case "invalid" -> HttpResponseStatus.BAD_REQUEST;
            default -> HttpResponseStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static JsonObject result(boolean ok, String code) {
        JsonObject body = new JsonObject();
        body.addProperty("ok", ok);
        body.addProperty("code", code);
        return body;
    }

    private static int authenticate(FullHttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (header == null || header.isEmpty()) return 0;
        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        return AccessTokenService.verify(token);
    }

    private static void sendJson(
            ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, JsonObject body) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        applyCors(req, response);
        send(ctx, req, response);
    }

    private static void applyCors(FullHttpRequest req, FullHttpResponse response) {
        response.headers().set("Vary", "Origin");

        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Credentials", "true");
            response.headers().set("Access-Control-Allow-Methods", "POST, DELETE, OPTIONS");
            response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        }
    }

    private static void send(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse response) {
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        if (!response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
            response.headers()
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        }
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    private static boolean isKeepAlive(FullHttpRequest req) {
        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        if (connection != null && connection.equalsIgnoreCase("close")) return false;
        if (connection != null && connection.equalsIgnoreCase("keep-alive")) return true;
        return req.protocolVersion().isKeepAliveDefault();
    }
}
