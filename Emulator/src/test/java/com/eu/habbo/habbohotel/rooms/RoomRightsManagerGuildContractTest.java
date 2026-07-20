package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RoomRightsManagerGuildContractTest {
    private static final Pattern ACCEPTED_MEMBER_GUILD_RIGHTS_GUARD = Pattern.compile(
            "member\\.getMembershipStatus\\(\\)\\s*==\\s*GuildMembershipStatus\\.MEMBER\\s*&&\\s*guild\\.getRights\\(\\)",
            Pattern.MULTILINE);

    @Test
    void guildRoomRightsRequireAcceptedMembership() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomRightsManager.java"), StandardCharsets.UTF_8);

        assertTrue(
                ACCEPTED_MEMBER_GUILD_RIGHTS_GUARD.matcher(source).find(),
                "Guild room rights must only apply to accepted guild members.");
        assertFalse(
                source.contains("if (guild.getRights())"),
                "A guild room with shared rights must not grant GUILD_RIGHTS to non-members.");
    }

    @Test
    void legacyRoomGuildRightPathUsesSameMembershipGuard() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/Room.java"), StandardCharsets.UTF_8);

        assertTrue(
                source.contains("return this.rightsManager.getGuildRightLevel(habbo);"),
                "The legacy Room path must delegate to the characterized rights policy.");
    }
}
