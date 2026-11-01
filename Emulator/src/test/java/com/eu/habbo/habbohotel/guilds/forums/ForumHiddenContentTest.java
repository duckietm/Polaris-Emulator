package com.eu.habbo.habbohotel.guilds.forums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A hidden post kept its text on the wire, and the check on a hidden thread was inverted: one a group admin
 * hid (10) was served to every reader, one staff hid (20) to the group's admins.
 */
class ForumHiddenContentTest {

    @Test
    void whoSeesWhatEachStateHides() {
        assertTrue(ForumThreadState.visibleTo(ForumThreadState.OPEN, false, false));
        assertTrue(ForumThreadState.visibleTo(ForumThreadState.CLOSED, false, false));

        assertFalse(ForumThreadState.visibleTo(ForumThreadState.HIDDEN_BY_STAFF_MEMBER, false, false));
        assertTrue(ForumThreadState.visibleTo(ForumThreadState.HIDDEN_BY_STAFF_MEMBER, false, true));
        assertTrue(ForumThreadState.visibleTo(ForumThreadState.HIDDEN_BY_STAFF_MEMBER, true, false));

        assertFalse(ForumThreadState.visibleTo(ForumThreadState.HIDDEN_BY_GUILD_ADMIN, false, false));
        assertFalse(ForumThreadState.visibleTo(ForumThreadState.HIDDEN_BY_GUILD_ADMIN, false, true));
        assertTrue(ForumThreadState.visibleTo(ForumThreadState.HIDDEN_BY_GUILD_ADMIN, true, false));
    }

    @Test
    void aHiddenPostKeepsItsPlaceWithoutItsText() {
        ForumThreadComment hidden =
                new ForumThreadComment(5, 2, 9, "insult", 100, ForumThreadState.HIDDEN_BY_STAFF_MEMBER, 1);
        hidden.setIndex(3);

        ForumThreadComment shown = hidden.forReader(false, false);
        assertEquals("", shown.getMessage());
        assertEquals(3, shown.getIndex());
        assertEquals(9, shown.getUserId());
        assertEquals("insult", hidden.getMessage(), "the stored post is untouched");

        assertSame(hidden, hidden.forReader(false, true), "a group admin sees what a group admin hid");
    }

    @Test
    void theThreadHandlerAndTheReportUseTheRightChecks() throws Exception {
        String handler = Files.readString(
                        Path.of(
                                "src/main/java/com/eu/habbo/messages/incoming/guilds/forums/GuildForumThreadsMessagesEvent.java"))
                .replaceAll("\\s+", " ");
        String report = Files.readString(
                        Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool/ReportCommentEvent.java"))
                .replaceAll("\\s+", " ");

        assertTrue(handler.contains(
                "ForumThreadState.visibleTo(thread.getState(), hasStaffPermissions, isGuildAdministrator)"));
        assertTrue(handler.contains("comment.forReader(hasStaffPermissions, isGuildAdministrator)"));
        assertTrue(report.contains("comment.getThreadId() != threadId"));
        assertTrue(report.contains("thread.getGuildId() != groupId"));
        assertFalse(report.contains("thread.getOpenerId()"), "the comment's author is reported, not the thread's");
    }
}
