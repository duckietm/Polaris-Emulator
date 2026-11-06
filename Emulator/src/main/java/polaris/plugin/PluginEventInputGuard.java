package polaris.plugin;

public final class PluginEventInputGuard {
    private PluginEventInputGuard() {}

    public static boolean isPositiveId(int id) {
        return id > 0;
    }
}
