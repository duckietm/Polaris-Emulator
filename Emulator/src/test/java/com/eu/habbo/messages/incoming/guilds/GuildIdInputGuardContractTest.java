package com.eu.habbo.messages.incoming.guilds;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildIdInputGuardContractTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds");

    private static String source(String file) throws Exception {
        return Files.readString(SOURCE_ROOT.resolve(file));
    }

    @Test
    void guildPacketIdsUseTheSharedPositiveIdGuard() throws Exception {
        Map<String, String> expectedGuards = Map.ofEntries(
                Map.entry("GuildAcceptMembershipEvent.java", "GuildInputGuard.arePositiveIds(guildId, userId)"),
                Map.entry("GuildChangeBadgeEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("GuildChangeColorsEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("GuildChangeNameDescEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("GuildChangeSettingsEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("GuildConfirmRemoveMemberEvent.java", "GuildInputGuard.arePositiveIds(guildId, userId)"),
                Map.entry("GuildDeclineMembershipEvent.java", "GuildInputGuard.arePositiveIds(guildId, userId)"),
                Map.entry("GuildDeleteEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("GuildRemoveAdminEvent.java", "GuildInputGuard.arePositiveIds(guildId, userId)"),
                Map.entry("GuildRemoveFavoriteEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("GuildRemoveMemberEvent.java", "GuildInputGuard.arePositiveIds(guildId, userId)"),
                Map.entry("GuildSetAdminEvent.java", "GuildInputGuard.arePositiveIds(guildId, userId)"),
                Map.entry("GuildSetFavoriteEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("RequestGuildBuyEvent.java", "GuildInputGuard.isPositiveId(roomId)"),
                Map.entry("RequestGuildFurniWidgetEvent.java", "GuildInputGuard.arePositiveIds(itemId, guildId)"),
                Map.entry("RequestGuildInfoEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("RequestGuildJoinEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("RequestGuildManageEvent.java", "GuildInputGuard.isPositiveId(guildId)"),
                Map.entry("RequestGuildMembersEvent.java", "GuildInputGuard.isPositiveId(groupId)"));

        for (Map.Entry<String, String> entry : expectedGuards.entrySet()) {
            assertTrue(source(entry.getKey()).contains(entry.getValue()),
                    entry.getKey() + " must reject non-positive packet IDs before lookup");
        }
    }

    @Test
    void sharedGuardRejectsEveryNonPositiveId() throws Exception {
        String guard = source("GuildInputGuard.java");

        assertTrue(guard.contains("return id > 0;"));
        assertTrue(guard.contains("for (int id : ids)"));
        assertTrue(guard.contains("if (!isPositiveId(id))"));
    }
}
