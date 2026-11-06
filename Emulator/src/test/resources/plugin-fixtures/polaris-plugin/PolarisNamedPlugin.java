package fixture.polarisplugin;

import polaris.hotel.users.User;
import polaris.plugin.PolarisPlugin;

public final class PolarisNamedPlugin extends PolarisPlugin {
    private boolean enabled;

    @Override
    public void onEnable() {
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
}
