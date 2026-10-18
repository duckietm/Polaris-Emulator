package com.eu.habbo.messages.incoming.guilds.forums;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.guilds.forums.ThreadUpdatedMessageComposer;
import org.junit.jupiter.api.Test;

/** Forum posts need at least 11 characters, and thread moderation answers with the updated thread. */
class GuildForumOfficialAnswersTest {

    @Test
    void messagesAndSubjectsNeedElevenCharacters() {
        String ten = "x".repeat(10);
        String eleven = "x".repeat(11);

        assertFalse(GuildForumPostThreadEvent.validLengths(ten, eleven, true));
        assertFalse(GuildForumPostThreadEvent.validLengths(eleven, ten, true));
        assertTrue(GuildForumPostThreadEvent.validLengths(eleven, eleven, true));
        // A reply has no subject.
        assertTrue(GuildForumPostThreadEvent.validLengths(eleven, "", false));
        assertFalse(GuildForumPostThreadEvent.validLengths(ten, "", false));
    }

    @Test
    void moderationAnswersWithTheUpdatedThread() {
        Guild guild = mock(Guild.class);
        ForumThread thread = mock(ForumThread.class);
        when(thread.isPinned()).thenReturn(true);
        Habbo moderator = mock(Habbo.class);

        ThreadUpdatedMessageComposer answer = GuildForumModerateThreadEvent.updatedThread(guild, thread, moderator);

        assertSame(thread, answer.getThread());
        assertTrue(answer.isPinned());
        assertFalse(answer.isLocked());
    }
}
