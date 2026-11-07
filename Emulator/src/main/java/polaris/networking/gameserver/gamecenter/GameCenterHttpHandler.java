package polaris.networking.gameserver.gamecenter;

import polaris.hotel.games.battleball.BattleBallManager;
import polaris.hotel.games.center.GameCenter;
import polaris.hotel.games.fastfood.FastFoodManager;
import polaris.hotel.games.hall.HallManager;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.hotel.games.uno.UnoManager;
import polaris.hotel.games.werewolf.WerewolfManager;
import polaris.hotel.games.wobble.WobbleManager;
import polaris.networking.gameserver.auth.AccessTokenService;
import polaris.networking.gameserver.auth.CorsOriginGate;
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
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * `/api/gamecenter`: Habbi's Game Center for a logged-in player. `GET /` answers each game's players now and the
 * player's games left (SnowStorm: its players only, it keeps its own counter), and the packs of games;
 * `GET /leaderboard?game=&weekly=1&scope=everyone|friends|groups&week=0` a leaderboard; `POST /buy {game, offerId}`
 * buys a pack of games for one game and answers the list again.
 */
public class GameCenterHttpHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameCenterHttpHandler.class);

    static final String BASE_PATH = "/api/gamecenter";
    private static final int MAX_JSON_BYTES = 1024;

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
            LOGGER.error("[gamecenter] unexpected error path=" + path, e);
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

        if (action.isEmpty() && req.method() == HttpMethod.GET) {
            sendJson(ctx, req, HttpResponseStatus.OK, overview(userId));
        } else if (action.equals("leaderboard") && req.method() == HttpMethod.GET) {
            String game = param(req, "game");

            if (!GameCenter.known(game)) {
                sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Refused.", "no_game"));
                return;
            }

            sendJson(
                    ctx,
                    req,
                    HttpResponseStatus.OK,
                    GameCenter.get()
                            .leaderboard(
                                    userId,
                                    game,
                                    !"0".equals(param(req, "weekly")),
                                    param(req, "scope"),
                                    intParam(req, "week")));
        } else if (action.equals("buy") && req.method() == HttpMethod.POST) {
            JsonObject body = req.content().readableBytes() > MAX_JSON_BYTES ? new JsonObject() : readJson(req);
            String code = GameCenter.get()
                    .buy(userId, body.has("game") ? body.get("game").getAsString() : "", intOf(body, "offerId"));

            if (code != null) {
                sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Refused.", code));
                return;
            }

            sendJson(ctx, req, HttpResponseStatus.OK, overview(userId));
        } else {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("Not found.", "not_found"));
        }
    }

    private static JsonObject overview(int userId) {
        Map<String, Integer> playing = new HashMap<>(HallManager.get().playersNow());

        playing.put("uno", UnoManager.get().playersNow());
        playing.put("werewolf", WerewolfManager.get().playersNow());
        playing.put("wobble", WobbleManager.get().playersNow(false));
        playing.put("wobblenext", WobbleManager.get().playersNow(true));
        playing.put("battleball", BattleBallManager.get().playersNow());
        playing.put("fastfood", FastFoodManager.get().playersNow());

        JsonObject view = GameCenter.get().overview(userId, playing);

        view.addProperty("snowwar", SnowWarManager.getInstance().getPlayersNow());
        return view;
    }

    private static int intOf(JsonObject body, String key) {
        try {
            return body.has(key) ? body.get(key).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ---- HTTP plumbing ----

    private static int authenticate(FullHttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);

        if (header == null || header.isEmpty()) return 0;

        return AccessTokenService.verify(
                header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim());
    }

    private static String param(FullHttpRequest req, String name) {
        var values = new QueryStringDecoder(req.uri()).parameters().get(name);

        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    private static int intParam(FullHttpRequest req, String name) {
        try {
            return Integer.parseInt(param(req, name));
        } catch (NumberFormatException e) {
            return 0;
        }
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
