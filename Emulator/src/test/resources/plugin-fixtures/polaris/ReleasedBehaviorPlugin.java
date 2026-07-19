package fixture.polaris;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.HabboPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ReleasedBehaviorPlugin extends HabboPlugin implements EventListener {
    private boolean enabled;
    private boolean disabled;

    @Override
    public void onEnable() throws Exception {
        enabled = true;
        require("released-plugin-resource".equals(readOwnResource()));
        require(releasedCollectionBehavior());
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

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalStateException("Released Polaris plugin fixture contract failed");
        }
    }
}
