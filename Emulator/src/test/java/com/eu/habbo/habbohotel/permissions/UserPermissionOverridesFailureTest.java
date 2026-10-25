package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * A database error cached an empty list of overrides for five minutes, so a timed sanction (trade = 0) stopped
 * applying, and with the database down every permission check of a broadcast ran a query.
 */
class UserPermissionOverridesFailureTest {

    private static UserPermissionOverrides.UserOverride sanction() {
        return new UserPermissionOverrides.UserOverride("acc_trade", PermissionSetting.DISALLOWED, 0, "", 1, "Admin");
    }

    @Test
    void aFailedRefreshKeepsTheSanctionReadBefore() {
        AtomicLong millis = new AtomicLong(0);
        AtomicBoolean down = new AtomicBoolean(false);
        UserPermissionOverrides overrides = new UserPermissionOverrides(
                userId -> {
                    if (down.get()) throw new IllegalStateException("db down");
                    return List.of(sanction());
                },
                millis::get,
                () -> 0);

        assertEquals(PermissionSetting.DISALLOWED, overrides.find(7, "acc_trade"));

        down.set(true);
        millis.set(UserPermissionOverrides.CACHE_MS + 1);

        assertEquals(PermissionSetting.DISALLOWED, overrides.find(7, "acc_trade"), "the sanction still applies");
    }

    @Test
    void withNothingReadYetAFailureIsNotCachedAndNotRetriedAtEveryCheck() {
        AtomicLong millis = new AtomicLong(0);
        AtomicBoolean down = new AtomicBoolean(true);
        AtomicInteger loads = new AtomicInteger();
        UserPermissionOverrides overrides = new UserPermissionOverrides(
                userId -> {
                    loads.incrementAndGet();
                    if (down.get()) throw new IllegalStateException("db down");
                    return List.of(sanction());
                },
                millis::get,
                () -> 0);

        assertNull(overrides.find(7, "acc_trade"));
        assertNull(overrides.find(7, "acc_trade"));
        assertEquals(1, loads.get(), "no query per check while the database is down");

        down.set(false);
        millis.set(UserPermissionOverrides.RETRY_MS);

        assertEquals(PermissionSetting.DISALLOWED, overrides.find(7, "acc_trade"), "read as soon as it can be");
        assertEquals(2, loads.get());
    }
}
