package fixture.currentwiredsnapshot;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.api.WiredDelayedSnapshotProvider;
import com.eu.habbo.plugin.HabboPlugin;

public final class WiredSnapshotProviderPlugin extends HabboPlugin {
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
    public boolean hasPermission(Habbo habbo, String key) {
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
