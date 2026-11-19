package polaris.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client scripts the loaded plugins ship, read from their jars once per load. The client asks for this list and
 * loads each script by its id, so a hotel installs a plugin's client part by dropping the jar in plugins/, without
 * editing the client configuration.
 */
public final class PluginClientScripts {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginClientScripts.class);

    /** The largest client script a plugin may ship. */
    static final int MAX_SCRIPT_BYTES = 2 * 1024 * 1024;

    static final int MAX_SCRIPT_PATH_LENGTH = 255;
    private static final int MAX_ID_LENGTH = 64;

    // Segments start with a letter, digit, '_' or '-', so "..", "." and hidden names cannot be named.
    private static final Pattern SCRIPT_PATH = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*(/[A-Za-z0-9_-][A-Za-z0-9._-]*)*\\.js");

    public static final PluginClientScripts EMPTY = new PluginClientScripts(List.of());

    private final List<Script> scripts;
    private final Map<String, Script> byId;

    private PluginClientScripts(List<Script> scripts) {
        this.scripts = List.copyOf(scripts);
        Map<String, Script> index = new LinkedHashMap<>();
        for (Script script : this.scripts) {
            index.put(script.id(), script);
        }
        this.byId = Map.copyOf(index);
    }

    /** The scripts in plugin name order. */
    public List<Script> scripts() {
        return this.scripts;
    }

    /** The script served under this id, or null. */
    public Script find(String id) {
        return id == null ? null : this.byId.get(id);
    }

    /** Reads the client script of every plugin that declares one; a script that cannot be served is skipped with the reason. */
    public static PluginClientScripts collect(Collection<PolarisPlugin> plugins) {
        List<PolarisPlugin> declaring = new ArrayList<>();
        for (PolarisPlugin plugin : plugins) {
            if (plugin != null
                    && plugin.configuration != null
                    && plugin.configuration.client != null
                    && plugin.configuration.client.script != null) {
                declaring.add(plugin);
            }
        }
        declaring.sort(Comparator.comparing(plugin -> plugin.configuration.name, String.CASE_INSENSITIVE_ORDER));

        List<Script> scripts = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (PolarisPlugin plugin : declaring) {
            String name = plugin.configuration.name;
            String path = plugin.configuration.client.script.trim();
            String problem = scriptPathProblem(path);
            if (problem != null) {
                LOGGER.warn("Plugin {} declares the client script \"{}\": {}. Its client part is not served.", name, path, problem);
                continue;
            }

            byte[] content;
            try {
                content = read(plugin.classLoader, path);
            } catch (IOException e) {
                LOGGER.warn("Plugin {} client script {} could not be read: {}. Its client part is not served.", name, path, e.getMessage());
                continue;
            }

            if (content == null) {
                LOGGER.warn("Plugin {} declares the client script {}, which its jar does not contain. Its client part is not served.", name, path);
                continue;
            }

            scripts.add(new Script(name, idFor(name, taken), content, integrity(content)));
        }

        return scripts.isEmpty() ? EMPTY : new PluginClientScripts(scripts);
    }

    /** Why a declared script path cannot be served, or null when it can. */
    static String scriptPathProblem(String path) {
        if (path == null || path.isEmpty()) return "the path is empty";
        if (path.length() > MAX_SCRIPT_PATH_LENGTH) return "the path is longer than " + MAX_SCRIPT_PATH_LENGTH + " characters";
        if (!SCRIPT_PATH.matcher(path).matches()) {
            return "it must be a relative path inside the jar, of letters, digits, '.', '_', '-' and '/', ending in .js";
        }
        return null;
    }

    /** The id a script is served under: the plugin name in lower case, made safe for a URL, unique in the list. */
    static String idFor(String name, Set<String> taken) {
        String id = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        id = id.replaceAll("^[-.]+|[-.]+$", "");
        if (id.length() > MAX_ID_LENGTH) id = id.substring(0, MAX_ID_LENGTH).replaceAll("[-.]+$", "");
        if (id.isEmpty()) id = "plugin";

        String unique = id;
        for (int n = 2; !taken.add(unique); n++) {
            unique = id + "-" + n;
        }
        return unique;
    }

    /** The Subresource Integrity value of the content, which the client puts on the script element. */
    static String integrity(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-384").digest(content);
            return "sha384-" + Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-384 is not available", e);
        }
    }

    // Looked up in the plugin's own jar only: getResource asks the emulator first, which could answer with its own file.
    private static byte[] read(ClassLoader classLoader, String path) throws IOException {
        if (!(classLoader instanceof URLClassLoader urlClassLoader)) return null;

        URL url = urlClassLoader.findResource(path);
        if (url == null) return null;

        URLConnection connection = url.openConnection();
        // A cached jar connection keeps the jar open, so a reload could not replace it on Windows.
        connection.setUseCaches(false);
        try (InputStream stream = connection.getInputStream()) {
            byte[] content = stream.readNBytes(MAX_SCRIPT_BYTES + 1);
            if (content.length > MAX_SCRIPT_BYTES) {
                throw new IOException("it is larger than " + MAX_SCRIPT_BYTES + " bytes");
            }
            return content;
        }
    }

    /** One plugin's client script. */
    public static final class Script {
        private final String name;
        private final String id;
        private final byte[] content;
        private final String integrity;

        Script(String name, String id, byte[] content, String integrity) {
            this.name = name;
            this.id = id;
            this.content = content.clone();
            this.integrity = integrity;
        }

        /** The plugin name from its plugin.json. */
        public String name() {
            return this.name;
        }

        /** The id in the script's URL. */
        public String id() {
            return this.id;
        }

        /** The script as the jar holds it. */
        public byte[] content() {
            return this.content.clone();
        }

        public int length() {
            return this.content.length;
        }

        /** The Subresource Integrity value of the content. */
        public String integrity() {
            return this.integrity;
        }

        @Override
        public String toString() {
            return this.name + " (" + this.id + ", " + this.content.length + " bytes)";
        }
    }
}
