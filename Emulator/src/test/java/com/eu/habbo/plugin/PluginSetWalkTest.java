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
            boolean changed = false;

            for (HabboPlugin ignored : manager.getPlugins()) {
                // The reload lands in the middle of the walk. Which of the new plugins the walk still sees is not
                // defined (the set is weakly consistent), so the test only asks that it does not throw.
                if (!changed) {
                    changed = true;
                    manager.getPlugins().add(new StubPlugin("added-0"));
                    manager.getPlugins().add(new StubPlugin("added-1"));
                    manager.getPlugins().add(new StubPlugin("added-2"));
                }
            }
        });

        assertEquals(5, manager.getPlugins().size());
    }
}
