package polaris.networking.gameserver.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import polaris.hotel.users.User;
import polaris.plugin.PluginClientConfiguration;
import polaris.plugin.PluginClientScripts;
import polaris.plugin.PluginConfiguration;
import polaris.plugin.PolarisPlugin;

class PluginClientHttpHandlerTest {

    private static final String SCRIPT = "window.OctanePlugins.register({ name: 'shop' });";

    @TempDir
    Path temporaryDirectory;

    private URLClassLoader loader;
    private PluginClientScripts scripts;

    @BeforeEach
    void loadAPlugin() throws IOException {
        Path jar = this.temporaryDirectory.resolve("shop.jar");
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            out.putNextEntry(new JarEntry("client/shop.js"));
            out.write(SCRIPT.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        this.loader =
                new URLClassLoader(new URL[] {jar.toUri().toURL()}, getClass().getClassLoader());

        TestPlugin plugin = new TestPlugin();
        plugin.configuration = new PluginConfiguration();
        plugin.configuration.name = "Shop";
        plugin.configuration.main = TestPlugin.class.getName();
        plugin.configuration.client = new PluginClientConfiguration();
        plugin.configuration.client.script = "client/shop.js";
        plugin.classLoader = this.loader;

        this.scripts = PluginClientScripts.collect(List.of(plugin));
    }

    @AfterEach
    void close() throws IOException {
        this.loader.close();
    }

    @Test
    void listsEachScriptWithItsUrlAndIntegrity() {
        FullHttpResponse response = send(HttpMethod.GET, "/api/client-plugins");

        assertEquals(HttpResponseStatus.OK, response.status());
        JsonArray plugins = JsonParser.parseString(response.content().toString(StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonArray("plugins");
        assertEquals(1, plugins.size());
        JsonObject entry = plugins.get(0).getAsJsonObject();
        assertEquals("Shop", entry.get("name").getAsString());
        assertEquals("/api/client-plugins/shop.js", entry.get("url").getAsString());
        assertEquals(
                this.scripts.find("shop").integrity(), entry.get("integrity").getAsString());
        ReferenceCountUtil.release(response);
    }

    @Test
    void servesTheScriptAsJavaScriptAndAnswersARepeatWithNotModified() {
        FullHttpResponse response = send(HttpMethod.GET, "/api/client-plugins/shop.js");

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(SCRIPT, response.content().toString(StandardCharsets.UTF_8));
        assertEquals("text/javascript; charset=UTF-8", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals("nosniff", response.headers().get("X-Content-Type-Options"));
        String etag = response.headers().get(HttpHeaderNames.ETAG);
        assertNotNull(etag);
        ReferenceCountUtil.release(response);

        FullHttpRequest again = request(HttpMethod.GET, "/api/client-plugins/shop.js");
        again.headers().set(HttpHeaderNames.IF_NONE_MATCH, etag);
        FullHttpResponse cached = send(again);

        assertEquals(HttpResponseStatus.NOT_MODIFIED, cached.status());
        assertEquals(0, cached.content().readableBytes());
        ReferenceCountUtil.release(cached);
    }

    @Test
    void anUnknownScriptIsNotFoundAndOnlyGetIsAnswered() {
        FullHttpResponse unknown = send(HttpMethod.GET, "/api/client-plugins/other.js");
        assertEquals(HttpResponseStatus.NOT_FOUND, unknown.status());
        ReferenceCountUtil.release(unknown);

        FullHttpResponse noSuffix = send(HttpMethod.GET, "/api/client-plugins/shop");
        assertEquals(HttpResponseStatus.NOT_FOUND, noSuffix.status());
        ReferenceCountUtil.release(noSuffix);

        FullHttpResponse post = send(HttpMethod.POST, "/api/client-plugins");
        assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, post.status());
        ReferenceCountUtil.release(post);
    }

    @Test
    void anEmptyOrMissingListIsAnEmptyArray() {
        this.scripts = null;
        FullHttpResponse response = send(HttpMethod.GET, "/api/client-plugins");

        assertEquals("{\"plugins\":[]}", response.content().toString(StandardCharsets.UTF_8));
        ReferenceCountUtil.release(response);
    }

    @Test
    void otherPathsPassThrough() {
        EmbeddedChannel channel = new EmbeddedChannel(new PluginClientHttpHandler(() -> this.scripts));
        channel.writeInbound(request(HttpMethod.GET, "/api/client-pluginsx"));

        assertNull(channel.readOutbound());
        FullHttpRequest passed = channel.readInbound();
        assertNotNull(passed);
        ReferenceCountUtil.release(passed);
        channel.finishAndReleaseAll();
    }

    private FullHttpResponse send(HttpMethod method, String uri) {
        return send(request(method, uri));
    }

    // The handler closes the connection after each answer, as the other web API handlers do: one channel per request.
    private FullHttpResponse send(FullHttpRequest request) {
        EmbeddedChannel channel = new EmbeddedChannel(new PluginClientHttpHandler(() -> this.scripts));
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();
        assertNotNull(response);
        channel.finish();
        return response;
    }

    private static FullHttpRequest request(HttpMethod method, String uri) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
    }

    static final class TestPlugin extends PolarisPlugin {
        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}

        @Override
        public boolean hasPermission(User user, String key) {
            return false;
        }
    }
}
