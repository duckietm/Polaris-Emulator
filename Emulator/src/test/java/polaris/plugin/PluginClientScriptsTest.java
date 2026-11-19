package polaris.plugin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import polaris.hotel.users.User;

class PluginClientScriptsTest {

    @TempDir
    Path temporaryDirectory;

    private final List<URLClassLoader> loaders = new ArrayList<>();

    @AfterEach
    void closeLoaders() throws IOException {
        for (URLClassLoader loader : this.loaders) {
            loader.close();
        }
    }

    @Test
    void readsTheDeclaredScriptFromThePluginJarWithItsIntegrity() throws Exception {
        byte[] script = "OctanePlugins.register({ name: 'rp' });".getBytes(StandardCharsets.UTF_8);
        TestPlugin plugin = plugin("Polaris RP", "client/rp.js", Map.of("client/rp.js", script));

        PluginClientScripts scripts = PluginClientScripts.collect(List.of(plugin));

        assertEquals(1, scripts.scripts().size());
        PluginClientScripts.Script found = scripts.find("polaris-rp");
        assertNotNull(found);
        assertEquals("Polaris RP", found.name());
        assertArrayEquals(script, found.content());
        String expected = "sha384-"
                + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-384").digest(script));
        assertEquals(expected, found.integrity());
    }

    @Test
    void skipsAPluginWithoutAClientPartAMissingFileAndAPathOutsideTheJar() throws Exception {
        byte[] script = "1;".getBytes(StandardCharsets.UTF_8);
        TestPlugin serverOnly = plugin("Server only", null, Map.of());
        TestPlugin missing = plugin("Missing", "client/missing.js", Map.of());
        TestPlugin escaping = plugin("Escaping", "../secret.js", Map.of("client/x.js", script));
        TestPlugin absolute = plugin("Absolute", "/client/x.js", Map.of("client/x.js", script));
        TestPlugin notScript = plugin("Not a script", "client/x.json", Map.of("client/x.json", script));

        PluginClientScripts scripts =
                PluginClientScripts.collect(List.of(serverOnly, missing, escaping, absolute, notScript));

        assertSame(PluginClientScripts.EMPTY, scripts);
    }

    @Test
    void refusesAScriptLargerThanTheLimit() throws Exception {
        byte[] large = new byte[PluginClientScripts.MAX_SCRIPT_BYTES + 1];
        TestPlugin plugin = plugin("Large", "client/large.js", Map.of("client/large.js", large));

        assertSame(PluginClientScripts.EMPTY, PluginClientScripts.collect(List.of(plugin)));
    }

    @Test
    void listsScriptsInNameOrderWithUniqueIds() throws Exception {
        byte[] script = "1;".getBytes(StandardCharsets.UTF_8);
        TestPlugin second = plugin("Shop", "shop.js", Map.of("shop.js", script));
        TestPlugin first = plugin("Arcade", "a/b/arcade.js", Map.of("a/b/arcade.js", script));
        TestPlugin twin = plugin("shop", "shop.js", Map.of("shop.js", script));

        PluginClientScripts scripts = PluginClientScripts.collect(List.of(second, first, twin));

        assertEquals(List.of("arcade", "shop", "shop-2"),
                scripts.scripts().stream().map(PluginClientScripts.Script::id).toList());
    }

    @Test
    void scriptPathsStayInsideTheJarAndEndInJs() {
        assertNull(PluginClientScripts.scriptPathProblem("client/my-plugin.js"));
        assertNull(PluginClientScripts.scriptPathProblem("plugin.min.js"));
        assertNotNull(PluginClientScripts.scriptPathProblem(""));
        assertNotNull(PluginClientScripts.scriptPathProblem("../plugin.js"));
        assertNotNull(PluginClientScripts.scriptPathProblem("client/../plugin.js"));
        assertNotNull(PluginClientScripts.scriptPathProblem("/client/plugin.js"));
        assertNotNull(PluginClientScripts.scriptPathProblem("client\\plugin.js"));
        assertNotNull(PluginClientScripts.scriptPathProblem("client/.hidden.js"));
        assertNotNull(PluginClientScripts.scriptPathProblem("https://example.com/plugin.js"));
        assertNotNull(PluginClientScripts.scriptPathProblem("client/plugin.css"));
        assertNotNull(PluginClientScripts.scriptPathProblem("a".repeat(256) + ".js"));
    }

    @Test
    void idsAreSafeForAUrl() {
        Set<String> taken = new HashSet<>();

        assertEquals("polaris-rp", PluginClientScripts.idFor("Polaris RP", taken));
        assertEquals("plugin", PluginClientScripts.idFor("   ", taken));
        assertEquals("a.b_c-d", PluginClientScripts.idFor("..A.b_c-d!!", taken));
        assertTrue(PluginClientScripts.idFor("x".repeat(100), taken).length() <= 64);
        assertEquals("polaris-rp-2", PluginClientScripts.idFor("polaris/rp", taken));
    }

    private TestPlugin plugin(String name, String script, Map<String, byte[]> entries) throws IOException {
        Path jar = Files.createTempFile(this.temporaryDirectory, "plugin", ".jar");
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }

        URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, getClass().getClassLoader());
        this.loaders.add(loader);

        TestPlugin plugin = new TestPlugin();
        plugin.configuration = new PluginConfiguration();
        plugin.configuration.name = name;
        plugin.configuration.main = TestPlugin.class.getName();
        if (script != null) {
            plugin.configuration.client = new PluginClientConfiguration();
            plugin.configuration.client.script = script;
        }
        plugin.classLoader = loader;
        return plugin;
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
