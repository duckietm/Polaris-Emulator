package polaris.plugin;

/**
 * The optional {@code client} section of a plugin.json: the script the client loads for this plugin, as a path inside
 * the plugin jar, for example {@code "client": { "script": "client/my-plugin.js" }}.
 */
public class PluginClientConfiguration {
    public String script;
}
