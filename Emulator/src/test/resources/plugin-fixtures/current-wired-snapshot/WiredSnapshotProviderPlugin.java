package fixture.currentwiredsnapshot;

import polaris.hotel.users.User;
import polaris.hotel.wired.api.WiredDelayedSnapshotProvider;
import polaris.plugin.PolarisPlugin;

public final class WiredSnapshotProviderPlugin extends PolarisPlugin {
    private boolean enabled;

    @Override
    public void onEnable() {
        SnapshotValue live = new SnapshotValue(17);
        SnapshotValue detached = live.snapshotForDelayedExecution();
        if (detached == live || detached.value != 17) {
            throw new IllegalStateException("Current WIRED snapshot provider contract failed");
        }
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    @Override
    public boolean hasPermission(User user, String key) {
        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static final class SnapshotValue implements WiredDelayedSnapshotProvider<SnapshotValue> {
        private final int value;

        private SnapshotValue(int value) {
            this.value = value;
        }

        @Override
        public SnapshotValue snapshotForDelayedExecution() {
            return new SnapshotValue(value);
        }
    }
}
