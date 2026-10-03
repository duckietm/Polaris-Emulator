package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.database.SqlQueries;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The emergency brake of the panel: while it is on, only the highest rank may use housekeeping,
 * so a compromised staff account cannot act. Kept in emulator_settings (housekeeping.lockdown),
 * so it survives a restart; read once and cached, written through on every change.
 */
final class HousekeepingLockdown {
    private static final Logger LOGGER = LoggerFactory.getLogger(HousekeepingLockdown.class);

    static final String SETTING_KEY = "housekeeping.lockdown";
    static final String READ_SQL = "SELECT `value` FROM emulator_settings WHERE `key` = ? LIMIT 1";
    static final String WRITE_SQL = "INSERT INTO emulator_settings (`key`, `value`) VALUES (?, ?) "
            + "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)";

    private static final HousekeepingLockdown INSTANCE = new HousekeepingLockdown();

    /** Null until the stored value has been read. */
    private final AtomicReference<Boolean> locked = new AtomicReference<>();

    private HousekeepingLockdown() {}

    static boolean isLocked() {
        Boolean cached = INSTANCE.locked.get();

        if (cached != null) return cached;

        boolean stored;

        try {
            stored = SqlQueries.queryOne(READ_SQL, set -> "1".equals(set.getString("value")), SETTING_KEY)
                    .orElse(false);
        } catch (SqlQueries.DataAccessException e) {
            // Unreadable: keep the panel usable rather than locking every operator out.
            LOGGER.error("Could not read the housekeeping lockdown", e);
            return false;
        }

        INSTANCE.locked.compareAndSet(null, stored);
        return INSTANCE.locked.get();
    }

    /** Stores and applies the lockdown; false when it could not be saved (nothing changes then). */
    static boolean set(boolean enabled) {
        try {
            SqlQueries.update(WRITE_SQL, SETTING_KEY, enabled ? "1" : "0");
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Could not save the housekeeping lockdown", e);
            return false;
        }

        INSTANCE.locked.set(enabled);
        return true;
    }
}
