package com.eu.habbo.messages.incoming.guilds;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A user who had only asked to join a group could be made its admin. */
class GuildSetAdminMemberOnlyContractTest {

    @Test
    void theHandlerPromotesOnlyAnAcceptedMember() throws Exception {
        String event = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/GuildSetAdminEvent.java"));

        int check = event.indexOf("target.getRank() != GuildRank.MEMBER");
        assertTrue(check > 0 && check < event.indexOf("new GuildGivenAdminEvent("));
    }

    @Test
    void theUpdateTouchesOnlyAMemberRow() throws Exception {
        String manager = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/guilds/GuildManager.java"));
        String setAdmin = manager.substring(manager.indexOf("public void setAdmin(Guild guild, int userId)"));

        assertTrue(setAdmin.contains("if (guild.getOwnerId() == userId) return;"));
        assertTrue(setAdmin.contains("AND level_id = ? LIMIT 1"));
        assertTrue(setAdmin.contains("statement.setInt(4, GuildRank.MEMBER.type);"));
    }
}
