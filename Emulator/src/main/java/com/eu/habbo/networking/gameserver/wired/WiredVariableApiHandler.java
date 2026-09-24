package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import com.eu.habbo.networking.gameserver.auth.AuthHttpUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves the Variables Web API ({@code /api/public/...}) on the websocket port. It runs on the
 * blocking HTTP executor group; the work per request is bounded by the router's limits.
 *
 * <p>It ships disabled: while {@code wired.api.enabled} is off every path under the prefix answers
 * 404, so a key that leaks before the operator has thought about exposure is worth nothing.
 */
public class WiredVariableApiHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredVariableApiHandler.class);
    private static final int MAX_QUERY_PARAMS = 16;
    private static final List<String> HEADERS = List.of("authorization", "x-api-key", "content-type", "origin");

    private static final class Shared {
        static final WiredApiRouter ROUTER = new WiredApiRouter(
                () -> WiredApiSettings.from(WiredPlatform.configuration()),
                new HotelWiredApiRooms(),
                new WiredApiLimits(System::currentTimeMillis));
    }

    private final WiredApiRouter router;

    public WiredVariableApiHandler() {
        this(null);
    }

    WiredVariableApiHandler(WiredApiRouter router) {
        this.router = router;
    }

    public static void logStartupStatus(String host, int port) {
        if (WiredApiSettings.from(WiredPlatform.configuration()).enabled()) {
            LOGGER.info("Started the Variables Web API on {}:{}{}", host, port, WiredApiRouter.PREFIX);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        QueryStringDecoder decoder;
        String path;
        try {
            decoder = new QueryStringDecoder(req.uri(), StandardCharsets.UTF_8, true, MAX_QUERY_PARAMS);
            path = decoder.path();
        } catch (IllegalArgumentException e) {
            super.channelRead(ctx, msg);
            return;
        }
        if (!WiredApiRouter.handles(path)) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            WiredApiResponse response;
            try {
                response = this.router().handle(toRequest(ctx, req, decoder, path));
            } catch (IllegalArgumentException e) {
                response = WiredApiResponse.json(400, WiredApiJson.error("bad_request", "Malformed request."));
            }
            write(ctx, req, response);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private WiredApiRouter router() {
        return this.router != null ? this.router : Shared.ROUTER;
    }

    private static WiredApiRequest toRequest(
            ChannelHandlerContext ctx, FullHttpRequest req, QueryStringDecoder decoder, String path) {
        Map<String, String> headers = new HashMap<>();
        for (String name : HEADERS) {
            String value = req.headers().get(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        byte[] body = new byte[req.content().readableBytes()];
        req.content().getBytes(req.content().readerIndex(), body);
        return new WiredApiRequest(req.method().name(), path, decoder.parameters(), headers, body, clientIp(ctx, req));
    }

    static String clientIp(ChannelHandlerContext ctx, FullHttpRequest req) {
        ConfigurationManager config = WiredPlatform.configuration();
        String ipHeader = config == null ? "" : config.getValue("ws.ip.header", "");
        if (ipHeader != null
                && !ipHeader.isEmpty()
                && req.headers().contains(ipHeader)
                && AuthHttpUtil.shouldHonorForwardedHeader(ctx, ipHeader)) {
            String value = req.headers().get(ipHeader);
            if (value != null && !value.isBlank()) {
                int comma = value.indexOf(',');
                return (comma > 0 ? value.substring(0, comma) : value).trim();
            }
        }
        String websocketIp = ctx.channel().attr(GameServerAttributes.WS_IP).get();
        if (websocketIp != null) {
            return websocketIp;
        }
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress address && address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private static void write(ChannelHandlerContext ctx, FullHttpRequest req, WiredApiResponse response) {
        byte[] body = response.body();
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(response.status()), Unpooled.wrappedBuffer(body));
        response.headers().forEach((name, value) -> httpResponse.headers().set(name, value));
        httpResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);

        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        boolean keepAlive = connection == null || !"close".equalsIgnoreCase(connection);
        if (keepAlive) {
            httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        var future = ctx.writeAndFlush(httpResponse);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
