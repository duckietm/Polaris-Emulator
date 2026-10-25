package com.eu.habbo.messages.incoming.guilds.forums;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Pinning or locking a forum thread checked the caller's rights in the guild named by the
 * packet, never that the thread belonged to it: an admin of one guild could pin and lock the
 * threads of every other guild.
 */
class ForumThreadUpdateGuildTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/forums/GuildForumThreadUpdateEvent.java");

    @Test
    void aThreadOfAnotherGuildIsRefusedBeforeAnyChange() throws Exception {
        String source = Files.readString(SOURCE);

        int ownership = source.indexOf("thread.getGuildId() != guildId");
        int rights = source.indexOf("boolean isStaff");
        int change = source.indexOf("thread.setPinned(isPinned);");

        assertTrue(ownership > 0, "the thread has to belong to the guild:\n" + source);
        assertTrue(ownership < rights && rights < change, "checked before the rights and before any change");
    }
}
