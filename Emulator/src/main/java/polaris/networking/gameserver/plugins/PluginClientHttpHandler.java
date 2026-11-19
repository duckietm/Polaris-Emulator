package polaris.networking.gameserver.plugins;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.util.function.Supplier;
import polaris.networking.gameserver.auth.CorsOriginGate;
import polaris.plugin.PluginClientScripts;

/**
 * Serves the client scripts of the loaded plugins: {@code GET /api/client-plugins} lists them with their integrity,
 * and {@code GET /api/client-plugins/<id>.js} serves one. The client loads every listed script, so a plugin's client
 * part needs no entry in the client configuration.
 */
public class PluginClientHttpHandler extends ChannelInboundHandlerAdapter {

    static final String BASE_PATH = "/api/client-plugins";
    private static final String SCRIPT_SUFFIX = ".js";
    private static final Gson GSON = new Gson();

    private final Supplier<PluginClientScripts> scripts;

    public PluginClientHttpHandler(Supplier<PluginClientScripts> scripts) {
        this.scripts = scripts;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!BASE_PATH.equals(path) && !path.startsWith(BASE_PATH + "/")) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            handle(ctx, req, path);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        if (req.method() == HttpMethod.OPTIONS) {
            send(ctx, req, HttpResponseStatus.NO_CONTENT, null, new byte[0], null);
            return;
        }

        if (req.method() != HttpMethod.GET && req.method() != HttpMethod.HEAD) {
            sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, "{\"error\":\"Use GET.\"}");
            return;
        }

        PluginClientScripts current = this.current();
        if (BASE_PATH.equals(path)) {
            sendJson(ctx, req, HttpResponseStatus.OK, listJson(current));
            return;
        }

        String file = path.substring(BASE_PATH.length() + 1);
        PluginClientScripts.Script script = file.endsWith(SCRIPT_SUFFIX)
                ? current.find(file.substring(0, file.length() - SCRIPT_SUFFIX.length()))
                : null;
        if (script == null) {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Not found.\"}");
            return;
        }

        // The integrity names this exact content, so it doubles as the validator for a cached copy.
        String etag = "\"" + script.integrity() + "\"";
        if (etag.equals(req.headers().get(HttpHeaderNames.IF_NONE_MATCH))) {
            send(ctx, req, HttpResponseStatus.NOT_MODIFIED, null, new byte[0], etag);
            return;
        }

        send(ctx, req, HttpResponseStatus.OK, "text/javascript; charset=UTF-8", script.content(), etag);
    }

    private PluginClientScripts current() {
        PluginClientScripts current = this.scripts.get();
        return current == null ? PluginClientScripts.EMPTY : current;
    }

    /** The list the client reads: each plugin's name, the URL of its script and the script's integrity. */
    static String listJson(PluginClientScripts scripts) {
        JsonArray plugins = new JsonArray();
        for (PluginClientScripts.Script script : scripts.scripts()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", script.name());
            entry.addProperty("url", BASE_PATH + "/" + script.id() + SCRIPT_SUFFIX);
            entry.addProperty("integrity", script.integrity());
            plugins.add(entry);
        }

        JsonObject body = new JsonObject();
        body.add("plugins", plugins);
        return GSON.toJson(body);
    }

    private static void sendJson(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String json) {
        send(ctx, req, status, "application/json; charset=UTF-8", json.getBytes(StandardCharsets.UTF_8), null);
    }

    private static void send(
            ChannelHandlerContext ctx,
            FullHttpRequest req,
            HttpResponseStatus status,
            String contentType,
            byte[] body,
            String etag) {
        boolean withBody = req.method() != HttpMethod.HEAD && body.length > 0;
        FullHttpResponse response = withBody
                ? new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(body))
                : new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        if (contentType != null) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, withBody || req.method() == HttpMethod.HEAD ? body.length : 0);
        response.headers().set(HttpHeaderNames.X_CONTENT_TYPE_OPTIONS, "nosniff");
        // The list changes with every plugin reload; a script is checked again against its ETag.
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        if (etag != null) {
            response.headers().set(HttpHeaderNames.ETAG, etag);
        }
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    // A script with an integrity is fetched in CORS mode, so the client's origin must be answered or the browser
    // refuses it. The origins are the hotel's own whitelist, as for the other web API calls.
    private static void applyCors(FullHttpRequest req, FullHttpResponse response) {
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, HEAD, OPTIONS");
            response.headers().set(HttpHeaderNames.VARY, "Origin");
        }
    }
}
