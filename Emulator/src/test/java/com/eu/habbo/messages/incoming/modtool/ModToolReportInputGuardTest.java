package com.eu.habbo.messages.incoming.modtool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModToolReportInputGuardTest {
    @Test
    void normalizesNullableMessages() {
        assertEquals("", ModToolReportInputGuard.normalize(null));
        assertEquals("report", ModToolReportInputGuard.normalize("  report  "));
    }

    @Test
    void reportMessagesMustBeNonEmptyAndBounded() {
        assertFalse(ModToolReportInputGuard.isValidReportMessage(""));
        assertFalse(ModToolReportInputGuard.isValidReportMessage(null));
        assertTrue(ModToolReportInputGuard.isValidReportMessage("a".repeat(ModToolReportInputGuard.MAX_REPORT_MESSAGE_LENGTH)));
        assertFalse(ModToolReportInputGuard.isValidReportMessage("a".repeat(ModToolReportInputGuard.MAX_REPORT_MESSAGE_LENGTH + 1)));
    }

    @Test
    void privateChatLogCountsAreBounded() {
        assertFalse(ModToolReportInputGuard.isValidPrivateChatLogCount(0));
        assertTrue(ModToolReportInputGuard.isValidPrivateChatLogCount(ModToolReportInputGuard.MAX_PRIVATE_CHAT_LOGS));
        assertFalse(ModToolReportInputGuard.isValidPrivateChatLogCount(ModToolReportInputGuard.MAX_PRIVATE_CHAT_LOGS + 1));
    }

    @Test
    void idsMustBePositive() {
        assertFalse(ModToolReportInputGuard.isPositiveId(0));
        assertFalse(ModToolReportInputGuard.isPositiveId(-1));
        assertTrue(ModToolReportInputGuard.isPositiveId(1));
    }
}
