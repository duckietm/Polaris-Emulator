package com.eu.habbo.messages.incoming.handshake;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AchievementLoginTrackingTest {
    @Test
    void countsConsecutiveCalendarDaysWithoutRecountingReconnects() {
        int midnight = 20000 * 86400;
        assertEquals(8, UsernameEvent.loginStreak(7, midnight - 1, midnight + 1));
        assertEquals(7, UsernameEvent.loginStreak(7, midnight + 1, midnight + 40000));
        assertEquals(1, UsernameEvent.loginStreak(7, midnight - 86401, midnight));
        assertEquals(1, UsernameEvent.loginStreak(0, 0, midnight));
    }
}
