package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Voucher codes could be guessed with no limit, :redeem deleted a wired chest named like a credit furni without
 * releasing what it held, registration took a name the rename refuses, and a member still waiting pinned threads.
 */
class RedeemAndNameGuardsTest {
    private static final Path MAIN = Path.of("src/main/java/com/eu/habbo");

    private static String source(String path) throws Exception {
        return Files.readString(MAIN.resolve(path)).replaceAll("\\s+", " ");
    }

    @Test
    void voucherAttemptsAreCountedPerUser() throws Exception {
        assertTrue(RedeemVoucherEvent.MAX_ATTEMPTS == 10);
        assertTrue(
                source("messages/incoming/catalog/RedeemVoucherEvent.java").contains("if (attempts > MAX_ATTEMPTS) {"));
    }

    @Test
    void redeemSkipsWiredChests() throws Exception {
        assertTrue(source("habbohotel/commands/RedeemCommand.java")
                .contains("|| item instanceof InteractionWiredChest) continue;"));
    }

    @Test
    void registrationRefusesABannedName() throws Exception {
        String session = source("networking/gameserver/auth/SessionEndpoints.java");

        assertTrue(session.indexOf("AccountChangeEndpoints.isUsernameBanned(conn, username)")
                < session.indexOf("int maxPerIp = config.getInt(\"register.max_per_ip\", 5);"));
    }

    @Test
    void onlyAcceptedMembersModerateThreads() throws Exception {
        assertTrue(source("messages/incoming/guilds/forums/GuildForumThreadUpdateEvent.java")
                .contains("if (member == null || member.getRank().type > GuildRank.MEMBER.type) {"));
    }
}
