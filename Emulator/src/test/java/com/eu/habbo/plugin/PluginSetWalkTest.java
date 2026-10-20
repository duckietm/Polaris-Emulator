package com.eu.habbo.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.users.Habbo;
import org.junit.jupiter.api.Test;

/**
 * A denied permission check walks the plugin set to ask each plugin; a plugin reload changes that set from another
 * thread. The walk must survive it instead of throwing out of a packet handler.
 */
class PluginSetWalkTest {
    private static final class StubPlugin extends HabboPlugin {
        StubPlugin(String name) {
            this.configuration = new HabboPluginConfiguration();
            this.configuration.name = name;
        }

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}

        @Override
        public boolean hasPermission(Habbo habbo, String key) {
            return false;
        }
    }

    @Test
    void theSetCanChangeWhileItIsBeingWalked() {
        PluginManager manager = new PluginManager();
        manager.getPlugins().add(new StubPlugin("one"));
        manager.getPlugins().add(new StubPlugin("two"));

        assertDoesNotThrow(() -> {
            int added = 0;

            for (HabboPlugin ignored : manager.getPlugins()) {
                if (added < 3) {
                    manager.getPlugins().add(new StubPlugin("added-" + added++));
                }
            }
        });

        assertEquals(5, manager.getPlugins().size());
    }
}
