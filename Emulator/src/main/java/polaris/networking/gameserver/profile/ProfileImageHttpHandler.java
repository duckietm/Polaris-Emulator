package polaris.networking.gameserver.profile;

import com.google.gson.JsonArray;
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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.users.User;
import polaris.hotel.users.infostand.InfostandBackgroundManager;
import polaris.hotel.users.infostand.InfostandBackgroundManager.Category;
import polaris.hotel.users.profile.AnimatedBanner;
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
    /** Which gallery choices the signed-in player may pick, and why the others are locked. */
    static final String OPTIONS_PATH = "/profile-images/options";
    /** {@code GET ?kind=} lists the player's own uploads of a kind, {@code DELETE ?kind=&id=} deletes one. */
    static final String LIBRARY_PATH = "/profile-images/library";
    /** {@code POST ?kind=&id=} puts one of the player's own uploads back on their profile. */
    static final String SELECT_PATH = "/profile-images/select";
    /** {@code POST ?value=rrggbb;rrggbb;angle;pattern} saves the player's own background, {@code value=} clears it. */
    static final String STYLE_PATH = "/profile-images/style";

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
            else if (path.equals(OPTIONS_PATH)) handleOptions(ctx, req);
            else if (path.equals(LIBRARY_PATH)) handleLibrary(ctx, req);
            else if (path.equals(SELECT_PATH)) handleSelect(ctx, req);
            else if (path.equals(STYLE_PATH)) handleStyle(ctx, req);
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
            byte[] picture = ByteBufUtil.getBytes(req.content());
            AnimatedBanner.Placement placement =
                    AnimatedBanner.isGif(picture) ? placement(new QueryStringDecoder(req.uri()).parameters()) : null;
            String file = this.service.store(userId, kind, picture, placement);
            JsonObject body = result(true, "ok");
            body.addProperty("file", file);
            sendJson(ctx, req, HttpResponseStatus.OK, body);
        } catch (ProfileImageException e) {
            sendJson(ctx, req, statusFor(e.getCode()), result(false, e.getCode()));
        }
    }

    /** Answers a preflight; true when the request was one. */
    private static boolean preflight(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() != HttpMethod.OPTIONS) return false;
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        return true;
    }

    private static Kind kindOf(Map<String, List<String>> parameters) {
        String key = first(parameters, "kind");
        return key == null ? null : Kind.fromKey(key);
    }

    private static int idOf(Map<String, List<String>> parameters) {
        try {
            return Integer.parseInt(first(parameters, "id"));
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    private void handleLibrary(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (preflight(ctx, req)) return;

        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, result(false, "unauthorized"));
            return;
        }

        Map<String, List<String>> parameters = new QueryStringDecoder(req.uri()).parameters();
        Kind kind = kindOf(parameters);
        if (kind == null) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, result(false, "invalid"));
            return;
        }

        if (req.method() == HttpMethod.DELETE) {
            if (!this.service.allowChange(userId)) {
                sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, result(false, "rate_limited"));
                return;
            }
            boolean forgotten = this.service.forget(userId, kind, idOf(parameters));
            sendJson(
                    ctx,
                    req,
                    forgotten ? HttpResponseStatus.OK : HttpResponseStatus.NOT_FOUND,
                    result(forgotten, forgotten ? "ok" : "not_found"));
            return;
        }

        if (req.method() != HttpMethod.GET) {
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, result(false, "invalid"));
            return;
        }

        JsonObject body = result(true, "ok");
        JsonArray items = new JsonArray();
        for (ProfileImageService.LibraryPicture picture : this.service.library(userId, kind)) {
            JsonObject item = new JsonObject();
            item.addProperty("id", picture.id());
            item.addProperty("file", picture.fileName());
            item.addProperty("createdAt", picture.createdAt());
            items.add(item);
        }
        body.add("items", items);
        sendJson(ctx, req, HttpResponseStatus.OK, body);
    }

    private void handleSelect(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (preflight(ctx, req)) return;

        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, result(false, "unauthorized"));
            return;
        }
        if (req.method() != HttpMethod.POST) {
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, result(false, "invalid"));
            return;
        }

        Map<String, List<String>> parameters = new QueryStringDecoder(req.uri()).parameters();
        Kind kind = kindOf(parameters);
        if (kind == null) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, result(false, "invalid"));
            return;
        }

        if (!this.service.allowChange(userId)) {
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, result(false, "rate_limited"));
            return;
        }

        try {
            boolean selected = this.service.select(userId, kind, idOf(parameters));
            sendJson(
                    ctx,
                    req,
                    selected ? HttpResponseStatus.OK : HttpResponseStatus.NOT_FOUND,
                    result(selected, selected ? "ok" : "not_found"));
        } catch (ProfileImageException e) {
            sendJson(ctx, req, statusFor(e.getCode()), result(false, e.getCode()));
        }
    }

    private void handleStyle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (preflight(ctx, req)) return;

        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, result(false, "unauthorized"));
            return;
        }
        if (req.method() != HttpMethod.POST) {
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, result(false, "invalid"));
            return;
        }

        String style = ProfileImageService.normalizeBackgroundStyle(
                first(new QueryStringDecoder(req.uri()).parameters(), "value"));
        if (style == null) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, result(false, "invalid"));
            return;
        }

        // The pattern over the colours is a card background of the gallery, with the same rules as picking it.
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        InfostandBackgroundManager manager = environment == null ? null : environment.getInfostandBackgroundManager();
        User user = environment == null ? null : environment.getUserManager().getUser(userId);
        int pattern = ProfileImageService.backgroundStylePattern(style);
        if (manager != null && !manager.canUse(user, Category.CARD, pattern)) {
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, result(false, "locked"));
            return;
        }

        if (!this.service.allowChange(userId)) {
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, result(false, "rate_limited"));
            return;
        }

        this.service.setBackgroundStyle(userId, style);
        sendJson(ctx, req, HttpResponseStatus.OK, result(true, "ok"));
    }

    /** Where the client placed an animated banner: scale and centre in the editor frame, and sharp pixels. */
    static AnimatedBanner.Placement placement(Map<String, List<String>> parameters) {
        return new AnimatedBanner.Placement(
                number(parameters, "scale"),
                number(parameters, "cx"),
                number(parameters, "cy"),
                "1".equals(first(parameters, "pixelated")));
    }

    private static double number(Map<String, List<String>> parameters, String key) {
        try {
            double value = Double.parseDouble(first(parameters, key));
            return Double.isFinite(value) ? value : Double.NaN;
        } catch (NumberFormatException | NullPointerException e) {
            return Double.NaN;
        }
    }

    private static String first(Map<String, List<String>> parameters, String key) {
        List<String> values = parameters.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * For each category of the customiser, the ids the hotel lists and, for the ones the player may not pick, why:
     * "hc", "ambassador" or "rank". The client shows those with a lock instead of a choice that is ignored.
     */
    private void handleOptions(ChannelHandlerContext ctx, FullHttpRequest req) {
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

        GameEnvironment environment = WiredPlatform.gameEnvironment();
        InfostandBackgroundManager manager = environment == null ? null : environment.getInfostandBackgroundManager();
        User user = environment == null ? null : environment.getUserManager().getUser(userId);
        JsonObject body = options(manager, user);
        body.addProperty("features", this.service.settings().features());
        sendJson(ctx, req, HttpResponseStatus.OK, body);
    }

    static JsonObject options(InfostandBackgroundManager manager, User user) {
        JsonObject body = result(true, "ok");
        boolean enforced = manager != null && manager.isEnforced();
        body.addProperty("enforced", enforced);

        JsonObject categories = new JsonObject();
        if (enforced) {
            for (Category category : Category.values()) {
                JsonObject listed = new JsonObject();
                for (Integer id : manager.entries(category).keySet()) {
                    String reason = user == null ? "offline" : manager.denialReason(user, category, id);
                    listed.addProperty(Integer.toString(id), reason == null ? "" : reason);
                }
                categories.add(category.dbValue, listed);
            }
        }
        body.add("categories", categories);
        return body;
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
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, fileName.endsWith(".gif") ? "image/gif" : "image/jpeg");
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
            response.headers().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
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
