package com.eu.habbo.habbohotel.mentions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MentionPageSizeTest {
    @Test
    void anUnsetOrNegativeSizeFallsBackToFifty() {
        assertEquals(50, MentionManager.normalizePageSize(0));
        assertEquals(50, MentionManager.normalizePageSize(-5));
    }

    @Test
    void aPageNeverExceedsTwoHundred() {
        assertEquals(200, MentionManager.normalizePageSize(201));
        assertEquals(200, MentionManager.normalizePageSize(5000));
    }

    @Test
    void aConfiguredSizeInRangeIsKept() {
        assertEquals(30, MentionManager.normalizePageSize(30));
        assertEquals(200, MentionManager.normalizePageSize(200));
    }
}
