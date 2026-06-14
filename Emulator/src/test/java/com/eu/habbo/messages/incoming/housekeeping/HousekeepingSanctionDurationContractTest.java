package com.eu.habbo.messages.incoming.housekeeping;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HousekeepingSanctionDurationContractTest {
    private static final Path BAN_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingBanUserEvent.java");
    private static final Path MUTE_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingMuteUserEvent.java");
    private static final Path TRADE_LOCK_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingTradeLockUserEvent.java");

    @Test
    void sanctionsUseSharedOverflowSafeDurationHelpers() throws IOException {
        String ban = Files.readString(BAN_SOURCE);
        String mute = Files.readString(MUTE_SOURCE);
        String tradeLock = Files.readString(TRADE_LOCK_SOURCE);

        assertTrue(ban.contains("HousekeepingSanctionDuration.secondsFromHours(hours)"));
        assertTrue(mute.contains("HousekeepingSanctionDuration.secondsFromMinutes(minutes)"));
        assertTrue(tradeLock.contains("HousekeepingSanctionDuration.secondsFromHours(hours)"));
        assertTrue(tradeLock.contains("HousekeepingSanctionDuration.unixUntil("));
    }

    @Test
    void sanctionsDoNotUseOverflowProneIntDurationConstants() throws IOException {
        String ban = Files.readString(BAN_SOURCE);
        String tradeLock = Files.readString(TRADE_LOCK_SOURCE);

        assertFalse(ban.contains("100 * 365 * 24 * 3600"));
        assertFalse(tradeLock.contains("100 * 365 * 24 * 3600"));
    }
}
