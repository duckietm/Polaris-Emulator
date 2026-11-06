package polaris.plugin;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import polaris.hotel.users.User;

public abstract class PolarisPlugin {
    public final Map<Class<? extends Event>, Set<Method>> registeredEvents = new HashMap<>();

    public PluginConfiguration configuration;
    public URLClassLoader classLoader;
    public InputStream stream;

    public abstract void onEnable() throws Exception;

    public abstract void onDisable() throws Exception;

    public boolean isRegistered(Class<? extends Event> clazz) {
        return this.registeredEvents.containsKey(clazz);
    }

    public abstract boolean hasPermission(User user, String key);
}
