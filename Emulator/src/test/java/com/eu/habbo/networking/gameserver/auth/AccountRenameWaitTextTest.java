package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** What a player is told when a rename is still on cooldown. */
class AccountRenameWaitTextTest {

    @Test
    void daysComeFirstAndAreSingularForOne() {
        assertEquals("1 day", AccountChangeEndpoints.renameWaitText(86_400L));
        assertEquals("1 day", AccountChangeEndpoints.renameWaitText(86_400L + 23 * 3600L));
        assertEquals("30 days", AccountChangeEndpoints.renameWaitText(30 * 86_400L));
    }

    @Test
    void hoursAreUsedWhenLessThanADayIsLeft() {
        assertEquals("1 hour", AccountChangeEndpoints.renameWaitText(3_600L));
        assertEquals("5 hours", AccountChangeEndpoints.renameWaitText(5 * 3_600L + 59 * 60L));
        assertEquals("23 hours", AccountChangeEndpoints.renameWaitText(86_399L));
    }

    @Test
    void lessThanAnHourIsNotReportedAsZeroHours() {
        assertEquals("less than an hour", AccountChangeEndpoints.renameWaitText(3_599L));
        assertEquals("less than an hour", AccountChangeEndpoints.renameWaitText(1L));
    }
}
