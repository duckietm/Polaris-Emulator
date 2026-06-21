package com.eu.habbo.plugin;

import com.eu.habbo.habbohotel.users.Habbo;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class HabboPlugin {
    public final Map<Class<? extends Event>, Set<Method>> registeredEvents = new HashMap<>();

    public HabboPluginConfiguration configuration;
    public URLClassLoader classLoader;
    public InputStream stream;

    public abstract void onEnable() throws Exception;

    public abstract void onDisable() throws Exception;

    public boolean isRegistered(Class<? extends Event> clazz) {
        return this.registeredEvents.containsKey(clazz);
    }

    public abstract boolean hasPermission(Habbo habbo, String key);
}
