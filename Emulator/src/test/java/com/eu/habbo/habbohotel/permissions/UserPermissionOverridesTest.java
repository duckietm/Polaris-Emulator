package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class UserPermissionOverridesTest {
    private static UserPermissionOverrides.UserOverride row(String key, PermissionSetting setting, int expiresAt) {
        return new UserPermissionOverrides.UserOverride(key, setting, expiresAt, "", 1, "Admin");
    }

    @Test
    void anActiveValueIsFoundAndAnExpiredOneIsNot() {
        AtomicInteger now = new AtomicInteger(1000);
        UserPermissionOverrides overrides = new UserPermissionOverrides(
                userId -> List.of(
                        row("cmd_kiss", PermissionSetting.DISALLOWED, 2000),
                        row("cmd_ha", PermissionSetting.ALLOWED, 0)),
                () -> 0L,
                now::get);

        assertEquals(PermissionSetting.DISALLOWED, overrides.find(7, "cmd_kiss"));
        assertEquals(PermissionSetting.ALLOWED, overrides.find(7, "cmd_ha"));
        assertNull(overrides.find(7, "cmd_alert"));

        now.set(2000);

        assertNull(overrides.find(7, "cmd_kiss"), "a timed value ends by itself");
        assertEquals(1, overrides.active(7).size());
    }

    @Test
    void rowsAreCachedUntilInvalidatedOrOld() {
        AtomicInteger loads = new AtomicInteger();
        AtomicLong millis = new AtomicLong(0);
        UserPermissionOverrides overrides = new UserPermissionOverrides(
                userId -> {
                    loads.incrementAndGet();
                    return List.of();
                },
                millis::get,
                () -> 0);

        overrides.find(7, "cmd_ha");
        overrides.find(7, "cmd_kiss");
        assertEquals(1, loads.get());

        overrides.invalidate(7);
        overrides.find(7, "cmd_ha");
        assertEquals(2, loads.get());

        millis.set(UserPermissionOverrides.CACHE_MS);
        overrides.find(7, "cmd_ha");
        assertEquals(3, loads.get(), "a row written from outside is seen after a few minutes");
    }

    @Test
    void durationsReadAsSeconds() {
        assertEquals(1800, UserPermissionOverrides.parseDuration("30m"));
        assertEquals(43200, UserPermissionOverrides.parseDuration("12h"));
        assertEquals(604800, UserPermissionOverrides.parseDuration("7d"));
        assertEquals(1209600, UserPermissionOverrides.parseDuration("2w"));
        assertEquals(0, UserPermissionOverrides.parseDuration("perm"));
        assertEquals(-1, UserPermissionOverrides.parseDuration("spam"));
        assertEquals(-1, UserPermissionOverrides.parseDuration("-5d"));
    }

    @Test
    void theExplanationShowsTheOverrideFirst() {
        Rank rank = new Rank(3);
        rank.setPermission("cmd_kiss", PermissionSetting.ALLOWED);

        List<String> lines = PermissionExplanation.explain(
                "Bob",
                "cmd_kiss",
                rank,
                true,
                PermissionExplanation.PluginCheck.NOT_NEEDED,
                row("cmd_kiss", PermissionSetting.DISALLOWED, 0));

        assertTrue(lines.get(1).startsWith("User override: 0 (denied), permanent, set by Admin"), lines.get(1));
        assertEquals("Result: DENIED (user override)", lines.get(lines.size() - 1));
    }
}
