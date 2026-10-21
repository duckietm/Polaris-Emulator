package com.eu.habbo.messages.incoming.handshake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void onlyTheFirstLoginOfADayIsReportedAsSuch() {
        int midnight = 20000 * 86400;
        assertTrue(UsernameEvent.isFirstLoginOfDay(midnight - 1, midnight + 1));
        assertFalse(UsernameEvent.isFirstLoginOfDay(midnight + 1, midnight + 40000));
        assertTrue(UsernameEvent.isFirstLoginOfDay(0, midnight), "a new account has no previous login");
    }
}
