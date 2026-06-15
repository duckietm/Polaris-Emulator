package com.eu.habbo.messages.incoming.guilds.forums;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildForumInputGuardContractTest {
    @Test
    void forumHandlersValidateClientProvidedIds() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/forums");

        for (String handler : List.of(
                "GuildForumPostThreadEvent.java",
                "GuildForumModerateMessageEvent.java",
                "GuildForumModerateThreadEvent.java",
                "GuildForumThreadUpdateEvent.java",
                "GuildForumThreadsEvent.java",
                "GuildForumThreadsMessagesEvent.java",
                "GuildForumMarkAsReadEvent.java",
                "GuildForumUpdateSettingsEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("GuildForumInputGuard.isPositiveId"),
                    handler + " must reject zero or negative client-provided ids");
        }
    }

    @Test
    void forumHandlersBoundExpensiveClientInputs() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/forums");

        String messages = Files.readString(base.resolve("GuildForumThreadsMessagesEvent.java"));
        String markRead = Files.readString(base.resolve("GuildForumMarkAsReadEvent.java"));
        String settings = Files.readString(base.resolve("GuildForumUpdateSettingsEvent.java"));
        String moderateThread = Files.readString(base.resolve("GuildForumModerateThreadEvent.java"));
        String moderateMessage = Files.readString(base.resolve("GuildForumModerateMessageEvent.java"));

        assertTrue(messages.contains("GuildForumInputGuard.isValidPage(index, limit)"),
                "thread message reads must bound index/limit before fetching comments");
        assertTrue(markRead.contains("GuildForumInputGuard.isValidMarkReadBatch(count)"),
                "mark-as-read must bound the client-provided batch count before DB writes");
        assertTrue(settings.contains("GuildForumInputGuard.isSettingsState"),
                "forum settings must reject unknown SettingsState values");
        assertTrue(moderateThread.contains("GuildForumInputGuard.isThreadModerationState(state)"),
                "thread moderation must reject unknown ForumThreadState values");
        assertTrue(moderateMessage.contains("GuildForumInputGuard.isMessageModerationState(state)"),
                "message moderation must reject unknown ForumThreadState values");
    }

    @Test
    void forumPostsNormalizeTextBeforeFiltering() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/forums/GuildForumPostThreadEvent.java"));

        assertTrue(source.contains("GuildForumInputGuard.normalize(this.packet.readString())"),
                "forum post subject and body should be normalized before word filtering and length checks");
    }
}
