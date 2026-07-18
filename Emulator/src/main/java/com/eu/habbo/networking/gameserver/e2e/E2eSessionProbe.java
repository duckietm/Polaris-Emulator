package com.eu.habbo.networking.gameserver.e2e;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.gameclients.GameClientManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class E2eSessionProbe {
    private static final String SESSION_COUNT_PATH = "/__e2e/session-count";
    private static final String ROOM_STATE_PATH = "/__e2e/room-state";
    private static final String DROP_PATH = "/__e2e/drop";

    private E2eSessionProbe() {
    }

    public static boolean tryHandle(FullHttpRequest request, ChannelHandlerContext context,
                                    GameClientManager clients, boolean enabled) {
        ProbeResponse probe = evaluate(request.method(), request.uri(),
                context.channel().remoteAddress(), clients, enabled);
        if (probe == null) return false;

        byte[] content = probe.body().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                probe.status(),
                Unpooled.wrappedBuffer(content));
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE);
        if (content.length > 0) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        }
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        return true;
    }

    public static ProbeResponse evaluate(HttpMethod method, String uri, SocketAddress remoteAddress,
                                         GameClientManager clients, boolean enabled) {
        if (!enabled || method == null || uri == null || clients == null) return null;

        QueryStringDecoder query = new QueryStringDecoder(uri);
        boolean sessionCount = SESSION_COUNT_PATH.equals(query.path()) && HttpMethod.GET.equals(method);
        boolean roomState = ROOM_STATE_PATH.equals(query.path()) && HttpMethod.GET.equals(method);
        boolean drop = DROP_PATH.equals(query.path()) && HttpMethod.POST.equals(method);
        if (!sessionCount && !roomState && !drop) return null;

        if (!isLoopback(remoteAddress)) {
            return new ProbeResponse(HttpResponseStatus.FORBIDDEN, "{\"error\":\"loopback only\"}");
        }

        int userId = parsePositiveUserId(query.parameters().get("userId"));
        if (userId <= 0) {
            return new ProbeResponse(HttpResponseStatus.BAD_REQUEST, "{\"error\":\"invalid userId\"}");
        }

        if (sessionCount) {
            return new ProbeResponse(
                    HttpResponseStatus.OK,
                    "{\"activeSessions\":" + clients.getAuthenticatedSessionCount(userId) + "}");
        }

        if (roomState) {
            return new ProbeResponse(HttpResponseStatus.OK, describeRoomState(clients.getAuthenticatedClient(userId)));
        }

        GameClient client = clients.getAuthenticatedClient(userId);
        if (client != null && client.getChannel() != null) {
            client.getChannel()
                    .writeAndFlush(new CloseWebSocketFrame(1001, "E2E server away"))
                    .addListener(ChannelFutureListener.CLOSE);
        }
        return new ProbeResponse(HttpResponseStatus.NO_CONTENT, "");
    }

    private static String describeRoomState(GameClient client) {
        int roomId = 0;
        int x = -1;
        int y = -1;
        int presenceIdentity = 0;
        long enteredAt = 0;

        Habbo habbo = client != null ? client.getHabbo() : null;
        if (habbo != null) {
            Room room = habbo.getHabboInfo().getCurrentRoom();
            RoomUnit unit = habbo.getRoomUnit();
            RoomTile location = unit != null ? unit.getCurrentLocation() : null;
            roomId = room != null ? room.getId() : 0;
            x = location != null ? location.x : -1;
            y = location != null ? location.y : -1;
            presenceIdentity = unit != null ? System.identityHashCode(unit) : 0;
            enteredAt = habbo.getHabboStats().roomEnterTimestamp;
        }

        return "{\"roomId\":" + roomId
                + ",\"x\":" + x
                + ",\"y\":" + y
                + ",\"presenceIdentity\":" + presenceIdentity
                + ",\"enteredAt\":" + enteredAt + "}";
    }

    private static int parsePositiveUserId(List<String> values) {
        if (values == null || values.size() != 1) return -1;
        try {
            int userId = Integer.parseInt(values.getFirst());
            return userId > 0 ? userId : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isLoopback(SocketAddress remoteAddress) {
        if (!(remoteAddress instanceof InetSocketAddress inetSocketAddress)) return false;
        InetAddress address = inetSocketAddress.getAddress();
        return address != null && address.isLoopbackAddress();
    }

    public record ProbeResponse(HttpResponseStatus status, String body) {
    }
}
