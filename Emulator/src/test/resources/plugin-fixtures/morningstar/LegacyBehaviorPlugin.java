package fixture.morningstar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.EventPriority;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.events.furniture.wired.WiredConditionFailedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public final class LegacyBehaviorPlugin extends HabboPlugin implements EventListener {
    private boolean enabled;
    private boolean disabled;
    private int eventCount;
    private int wiredTriggeredCount;
    private int wiredExecutedCount;
    private int wiredConditionFailedCount;

    @Override
    public void onEnable() throws Exception {
        enabled = true;
        require("legacy-plugin-resource".equals(readOwnResource()));
        require(bundledClasspathVisible());
        require(databaseBridgeSignatureIsCompatible());
        require(commonTroveMapBehavior());
        require(wiredHandlerSurfaceIsCompatible());
        require(wiredManagerSurfaceIsCompatible());
    }

    @Override
    public void onDisable() {
        disabled = true;
    }

    @Override
    public boolean hasPermission(Habbo habbo, String key) {
        return "fixture.allowed".equals(key);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFixtureEvent(FixtureEvent event) {
        eventCount++;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWiredStackTriggered(WiredStackTriggeredEvent event) {
        wiredTriggeredCount++;
        event.setCancelled(true);
    }

    @EventHandler
    public void onWiredStackExecuted(WiredStackExecutedEvent event) {
        wiredExecutedCount++;
    }

    @EventHandler
    public void onWiredConditionFailed(WiredConditionFailedEvent event) {
        wiredConditionFailedCount++;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public int getEventCount() {
        return eventCount;
    }

    public int getWiredTriggeredCount() {
        return wiredTriggeredCount;
    }

    public int getWiredExecutedCount() {
        return wiredExecutedCount;
    }

    public int getWiredConditionFailedCount() {
        return wiredConditionFailedCount;
    }

    public String readOwnResource() throws Exception {
        try (InputStream input = classLoader.getResourceAsStream("fixture/plugin-resource.txt")) {
            require(input != null);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }

    public boolean bundledClasspathVisible() throws Exception {
        String[] classes = {
                "com.google.gson.Gson",
                "com.zaxxer.hikari.HikariDataSource",
                "io.netty.channel.Channel",
                "it.unimi.dsi.fastutil.ints.Int2ObjectMap",
                "org.apache.commons.lang3.StringUtils"
        };
        for (String className : classes) {
            Class<?> type = Class.forName(className, false, classLoader);
            require(type.getClassLoader() != classLoader);
        }
        return true;
    }

    public boolean databaseBridgeSignatureIsCompatible() throws Exception {
        Method getDatabase = Emulator.class.getMethod("getDatabase");
        Method getDataSource = getDatabase.getReturnType().getMethod("getDataSource");
        return "com.zaxxer.hikari.HikariDataSource".equals(getDataSource.getReturnType().getName());
    }

    public boolean wiredHandlerSurfaceIsCompatible() throws Exception {
        Class<?> handler = Class.forName(
                "com.eu.habbo.habbohotel.wired.WiredHandler", false, classLoader);
        if (handler.getMethod("getGsonBuilder").invoke(null) == null) {
            return false;
        }

        Class<?> triggerType = Class.forName(
                "com.eu.habbo.habbohotel.wired.WiredTriggerType", false, classLoader);
        Class<?> roomUnit = Class.forName(
                "com.eu.habbo.habbohotel.rooms.RoomUnit", false, classLoader);
        Class<?> room = Class.forName(
                "com.eu.habbo.habbohotel.rooms.Room", false, classLoader);
        Object result = handler
                .getMethod("handle", triggerType, roomUnit, room, Object[].class)
                .invoke(null, new Object[] { null, null, null, null });
        return Boolean.FALSE.equals(result);
    }

    public boolean wiredManagerSurfaceIsCompatible() throws Exception {
        Class<?> manager = Class.forName(
                "com.eu.habbo.habbohotel.wired.core.WiredManager", false, classLoader);
        return Boolean.TRUE.equals(manager.getMethod("isExclusive").invoke(null));
    }

    public boolean commonTroveMapBehavior() {
        THashMap<String, Integer> values = new THashMap<>();
        values.put("answer", 42);
        return values.get("answer") == 42;
    }

    public int useMorningstarTroveSurface() {
        TIntObjectHashMap<String> values = new TIntObjectHashMap<>();
        values.put(7, "seven");
        THashMap<String, Integer> objects = new THashMap<>();
        objects.put("one", 1);
        objects.forEachEntry((key, value) -> value == 1);
        return values.size() + objects.size();
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalStateException("Legacy plugin fixture contract failed");
        }
    }

    public static final class FixtureEvent extends Event {
    }
}
