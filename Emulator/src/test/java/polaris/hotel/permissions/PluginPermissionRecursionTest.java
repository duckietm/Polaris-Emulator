package polaris.hotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.plugin.PolarisPlugin;

class PluginPermissionRecursionTest {
    private final User user = mock(User.class);
    private final List<PolarisPlugin> plugins = new ArrayList<>();

    private static PolarisPlugin plugin(BiPredicate<User, String> answer) {
        return new PolarisPlugin() {
            @Override
            public void onEnable() {}

            @Override
            public void onDisable() {}

            @Override
            public boolean hasPermission(User user, String key) {
                return answer.test(user, key);
            }
        };
    }

    @Test
    void aPluginThatAsksBackGetsTheRanksAnswerInsteadOfRecursing() {
        AtomicInteger asked = new AtomicInteger();
        // What `return user.hasPermission(key)` amounts to once the rank has said no.
        this.plugins.add(plugin((user, key) -> {
            asked.incrementAndGet();
            return PermissionsManager.anyPluginGrants(this.plugins, user, key);
        }));

        assertFalse(PermissionsManager.anyPluginGrants(this.plugins, this.user, "acc_supporttool"));
        assertEquals(1, asked.get());
    }

    @Test
    void aPluginStillGrantsItsOwnKeys() {
        this.plugins.add(plugin((user, key) -> key.equals("cmd_custom")));

        assertTrue(PermissionsManager.anyPluginGrants(this.plugins, this.user, "cmd_custom"));
        assertFalse(PermissionsManager.anyPluginGrants(this.plugins, this.user, "cmd_other"));
    }

    @Test
    void theGuardIsLiftedAfterEachQuestionEvenWhenAPluginThrows() {
        AtomicInteger asked = new AtomicInteger();
        this.plugins.add(plugin((user, key) -> {
            asked.incrementAndGet();
            if (key.equals("boom")) {
                throw new IllegalStateException("plugin failure");
            }
            return true;
        }));

        assertThrows(
                IllegalStateException.class, () -> PermissionsManager.anyPluginGrants(this.plugins, this.user, "boom"));
        assertTrue(PermissionsManager.anyPluginGrants(this.plugins, this.user, "cmd_custom"));
        assertEquals(2, asked.get());
    }
}
