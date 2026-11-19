package polaris.plugin;

public class PluginConfiguration {
    public String name;
    public String author;
    public String main;

    /** The script the client loads for this plugin; null when the plugin has no client part. */
    public PluginClientConfiguration client;
}
