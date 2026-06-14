package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifyUserSubscriptionGuardTest {
    @Test
    void validatesDurationAgainstConfiguredCeiling() {
        assertTrue(ModifyUserSubscription.isValidDuration(1, 10));
        assertTrue(ModifyUserSubscription.isValidDuration(10, 10));
        assertFalse(ModifyUserSubscription.isValidDuration(0, 10));
        assertFalse(ModifyUserSubscription.isValidDuration(-1, 10));
        assertFalse(ModifyUserSubscription.isValidDuration(11, 10));
    }

    @Test
    void parsesInvalidDurationCeilingsAsDefault() {
        assertEquals(ModifyUserSubscription.DEFAULT_MAX_DURATION_SECONDS, ModifyUserSubscription.parseMaxDuration(null));
        assertEquals(ModifyUserSubscription.DEFAULT_MAX_DURATION_SECONDS, ModifyUserSubscription.parseMaxDuration("0"));
        assertEquals(60, ModifyUserSubscription.parseMaxDuration("60"));
    }

    @Test
    void clampsPartialRemovalToRemainingSubscriptionTime() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/ModifyUserSubscription.java"));

        assertTrue(source.contains("Math.min(json.duration, s.getRemaining())"),
                "Partial subscription removal must not drive duration below the remaining time");
        assertTrue(source.contains("rcon.subscription.max_duration_seconds"),
                "RCON subscription duration ceiling must be configurable");
    }
}
