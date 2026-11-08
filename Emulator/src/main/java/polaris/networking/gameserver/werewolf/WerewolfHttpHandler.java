package polaris.networking.gameserver.werewolf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.games.werewolf.WerewolfException;
import polaris.hotel.games.werewolf.WerewolfManager;
import polaris.networking.gameserver.auth.AccessTokenService;
import polaris.networking.gameserver.auth.CorsOriginGate;

/**
 * `/api/werewolf`: The werewolf game for a logged-in player. `GET /` answers what they see (their village, the open
 * villages); `POST /create {rules}`, `/join {gameId, chair}`, `/leave`, `/sit {chair}`, `/rules {rules}`, `/bot`,
 * `/bots`, `/bot/remove {userId}`, `/start`, `/act {target, second}`, `/witch {save, poison}`, `/choose {yes}`,
 * `/pass`, `/ready`, `/say {text}` act and answer the same view. A refused action answers 400 with its code (`werewolf.error.<code>` in the client).
 */
public class WerewolfHttpHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(WerewolfHttpHandler.class);

    static final String BASE_PATH = "/api/werewolf";
    private static final int MAX_JSON_BYTES = 2 * 1024;
    private static final Set<String> ACTIONS = Set.of(
            "create",
            "join",
            "leave",
            "sit",
            "rules",
            "bot",
            "bots",
            "bot/remove",
            "start",
            "act",
            "witch",
            "choose",
            "pass",
            "ready",
            "say");

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
            this.handle(ctx, req, path);
        } catch (Exception e) {
            LOGGER.error("[werewolf] unexpected error path=" + path, e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("Server error.", "server_error"));
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        if (req.method() == HttpMethod.OPTIONS) {
            sendCors(ctx, req);
            return;
        }

        int userId = authenticate(req);

        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, error("Authentication required.", "unauthorized"));
            return;
        }

        String action = path.length() > BASE_PATH.length() ? path.substring(BASE_PATH.length() + 1) : "";

        while (action.endsWith("/")) action = action.substring(0, action.length() - 1);

        try {
            if (action.isEmpty() && req.method() == HttpMethod.GET) {
                sendJson(ctx, req, HttpResponseStatus.OK, WerewolfManager.get().view(userId));
            } else if (ACTIONS.contains(action) && req.method() == HttpMethod.POST) {
                JsonObject body = req.content().readableBytes() > MAX_JSON_BYTES ? new JsonObject() : readJson(req);

                sendJson(ctx, req, HttpResponseStatus.OK, WerewolfManager.get().act(userId, action, body));
            } else {
                sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("Not found.", "not_found"));
            }
        } catch (WerewolfException e) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Refused.", e.code()));
        }
    }

    // ---- HTTP plumbing ----

    private static int authenticate(FullHttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);

        if (header == null || header.isEmpty()) return 0;

        return AccessTokenService.verify(
                header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim());
    }

    private static JsonObject readJson(FullHttpRequest req) {
        try {
            String text = req.content().toString(StandardCharsets.UTF_8);

            return text.isEmpty()
                    ? new JsonObject()
                    : JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static JsonObject error(String message, String code) {
        JsonObject body = new JsonObject();

        body.addProperty("error", message);
        body.addProperty("code", code);
        return body;
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

        boolean keepAlive = HttpUtil.isKeepAlive(req);

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
        response.headers().set("Vary", "Origin");

        String origin = req.headers().get(HttpHeaderNames.ORIGIN);

        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Credentials", "true");
            response.headers().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        }
    }
}
