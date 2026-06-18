package com.eu.habbo.messages.incoming.guilds.forums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildForumInputGuardTest {
    @Test
    void normalizesNullableText() {
        assertEquals("", GuildForumInputGuard.normalize(null));
        assertEquals("hello", GuildForumInputGuard.normalize("  hello  "));
    }

    @Test
    void validatesIdsAndPaging() {
        assertFalse(GuildForumInputGuard.isPositiveId(0));
        assertTrue(GuildForumInputGuard.isPositiveId(1));
        assertFalse(GuildForumInputGuard.isValidPage(-1, 20));
        assertFalse(GuildForumInputGuard.isValidPage(0, 0));
        assertTrue(GuildForumInputGuard.isValidPage(0, GuildForumInputGuard.MAX_PAGE_LIMIT));
        assertFalse(GuildForumInputGuard.isValidPage(0, GuildForumInputGuard.MAX_PAGE_LIMIT + 1));
        assertTrue(GuildForumInputGuard.isValidThreadIndex(GuildForumInputGuard.MAX_THREAD_INDEX));
        assertFalse(GuildForumInputGuard.isValidThreadIndex(GuildForumInputGuard.MAX_THREAD_INDEX + 1));
    }

    @Test
    void validatesBatchAndStates() {
        assertFalse(GuildForumInputGuard.isValidMarkReadBatch(0));
        assertTrue(GuildForumInputGuard.isValidMarkReadBatch(GuildForumInputGuard.MAX_MARK_READ_BATCH));
        assertFalse(GuildForumInputGuard.isValidMarkReadBatch(GuildForumInputGuard.MAX_MARK_READ_BATCH + 1));

        assertTrue(GuildForumInputGuard.isSettingsState(0));
        assertTrue(GuildForumInputGuard.isSettingsState(3));
        assertFalse(GuildForumInputGuard.isSettingsState(4));

        assertTrue(GuildForumInputGuard.isThreadModerationState(20));
        assertFalse(GuildForumInputGuard.isThreadModerationState(999));
        assertTrue(GuildForumInputGuard.isMessageModerationState(10));
        assertFalse(GuildForumInputGuard.isMessageModerationState(0));
    }
}
