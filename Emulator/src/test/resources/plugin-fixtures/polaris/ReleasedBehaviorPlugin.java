package fixture.polaris;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.EventPriority;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.events.furniture.wired.WiredConditionFailedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ReleasedBehaviorPlugin extends HabboPlugin implements EventListener {
    private boolean enabled;
    private boolean disabled;
    private int wiredTriggeredCount;
    private int wiredExecutedCount;
    private int wiredConditionFailedCount;

    @Override
    public void onEnable() throws Exception {
        enabled = true;
        require("released-plugin-resource".equals(readOwnResource()));
        require(releasedCollectionBehavior());
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

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDisabled() {
        return disabled;
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

    public boolean releasedCollectionBehavior() {
        Map<?, ?> retainedReference = registeredEvents;
        return retainedReference == registeredEvents && retainedReference.isEmpty();
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

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalStateException("Released Polaris plugin fixture contract failed");
        }
    }
}
