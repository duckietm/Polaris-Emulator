package polaris.networking.gameserver.stats;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClientManager;
import polaris.hotel.users.User;
import polaris.monitoring.EmulatorStatsService;
import polaris.networking.gameserver.auth.AccessTokenService;
import polaris.networking.gameserver.auth.CorsOriginGate;

public class EmuStatsHttpHandler extends ChannelInboundHandlerAdapter {
    private static final String BASE_PATH = "/api/emustats";
    private static final Gson GSON = new Gson();
    /** Its own key: the page used to ask for acc_modtool_room_info, so every moderator could read it. */
    static final String PERMISSION = "acc_emustats";

    private static final String STALK_PERMISSION = "acc_can_stalk";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!BASE_PATH.equals(path)) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            handle(ctx, req);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() == HttpMethod.OPTIONS) {
            sendCors(ctx, req);
            return;
        }

        if (req.method() != HttpMethod.GET && req.method() != HttpMethod.HEAD) {
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, "{\"error\":\"Use GET.\"}");
            return;
        }

        int userId = authenticate(req);
        if (userId <= 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, "{\"error\":\"Unauthorized.\"}");
            return;
        }

        GameClientManager clients = Emulator.getGameServer().getGameClientManager();
        User user = clients.getUser(userId);
        if (user == null || !user.hasPermission(PERMISSION)) {
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, "{\"error\":\"Forbidden.\"}");
            return;
        }

        // A HEAD request only asks whether the page answers: the snapshot is not built for it.
        if (req.method() == HttpMethod.HEAD) {
            sendJson(ctx, req, HttpResponseStatus.OK, "{}");
            return;
        }

        JsonObject snapshot =
                GSON.toJsonTree(EmulatorStatsService.collectSnapshot()).getAsJsonObject();
        boolean mayStalk = user.hasPermission(STALK_PERMISSION);
        snapshot.add(
                "users",
                visibleUsers(
                        snapshot.getAsJsonArray("users"),
                        id -> !mayStalk && isHidden(clients.getUser(id)),
                        id -> !mayStalk && blocksFollowing(clients.getUser(id))));
        sendJson(ctx, req, HttpResponseStatus.OK, GSON.toJson(snapshot));
    }

    /**
     * The users list as the web page may show it: without the users who hide themselves, without the room of those who
     * do not want to be followed, and without anyone's credits. The server's own dashboard keeps the full list.
     */
    static JsonArray visibleUsers(JsonArray users, IntPredicate hidden, IntPredicate roomHidden) {
        JsonArray visible = new JsonArray();
        if (users == null) {
            return visible;
        }

        for (JsonElement element : users) {
            JsonObject user = element.getAsJsonObject().deepCopy();
            int id = user.get("id").getAsInt();
            if (hidden.test(id)) {
                continue;
            }

            user.remove("credits");
            if (roomHidden.test(id)) {
                user.addProperty("roomId", 0);
            }
            visible.add(user);
        }

        return visible;
    }

    private static boolean isHidden(User user) {
        return user != null
                && ((user.getRoomUnit() != null && user.getRoomUnit().isInvisible()) || user.getUserStats().hideOnline);
    }

    private static boolean blocksFollowing(User user) {
        return user != null && user.getUserStats().blockFollowing;
    }

    private static int authenticate(FullHttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (header == null || header.isBlank()) return 0;

        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        return AccessTokenService.verify(token);
    }

    private static void sendCors(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!CorsOriginGate.isAllowed(req)) {
            FullHttpResponse forbidden =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
            forbidden.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(forbidden).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyCors(req, response);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendJson(
            ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String json) {
        boolean headRequest = req.method() == HttpMethod.HEAD;
        byte[] bytes = (json == null ? "{}" : json).getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = headRequest
                ? new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status)
                : new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void applyCors(FullHttpRequest req, FullHttpResponse response) {
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS");
            response.headers()
                    .set(
                            HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS,
                            "Authorization, Content-Type, X-Requested-With, X-Nitro-Key, X-Nitro-Api");
            response.headers().set(HttpHeaderNames.VARY, "Origin");
        }
    }
}
