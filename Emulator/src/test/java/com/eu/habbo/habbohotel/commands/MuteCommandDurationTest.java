package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MuteCommandDurationTest {
    @Test
    void withoutDurationALongButBoundedMuteIsUsed() {
        int duration = MuteCommand.resolveDurationSeconds(new String[] {"mute", "Ghost"});

        assertEquals(MuteCommand.DEFAULT_SECONDS, duration);
        assertTrue(duration > 0 && duration < Integer.MAX_VALUE);
    }

    @Test
    void anExplicitDurationIsKept() {
        assertEquals(120, MuteCommand.resolveDurationSeconds(new String[] {"mute", "Ghost", "120"}));
    }

    @Test
    void hugeDurationsAreCapped() {
        assertEquals(
                MuteCommand.MAX_SECONDS,
                MuteCommand.resolveDurationSeconds(new String[] {"mute", "Ghost", "2147483647"}));
        assertEquals(
                MuteCommand.MAX_SECONDS,
                MuteCommand.resolveDurationSeconds(new String[] {"mute", "Ghost", "99999999999999"}));
    }

    @Test
    void invalidDurationsAreRejected() {
        assertEquals(-1, MuteCommand.resolveDurationSeconds(new String[] {"mute", "Ghost", "0"}));
        assertEquals(-1, MuteCommand.resolveDurationSeconds(new String[] {"mute", "Ghost", "-5"}));
        assertEquals(-1, MuteCommand.resolveDurationSeconds(new String[] {"mute", "Ghost", "abc"}));
    }
}
