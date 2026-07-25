package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildManager;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildMembershipStatus;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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

    @Test
    void guildRightsAreGrantedOnlyToAcceptedMembersOfASharedRightsGuild() {
        // Executable counterpart to the source-text guard above: a MEMBER of a shared-rights guild
        // gets GUILD_RIGHTS, but a habbo who has only requested membership (PENDING) gets NONE.
        Room room = RoomTestBuilder.room(41, 7).field("guild", 99).build();

        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(99);
        when(guild.getRights()).thenReturn(true);

        GuildManager guildManager = mock(GuildManager.class);
        when(guildManager.getGuild(99)).thenReturn(guild);

        GuildMember acceptedMember = mock(GuildMember.class);
        when(acceptedMember.getRank()).thenReturn(GuildRank.MEMBER);
        when(acceptedMember.getMembershipStatus()).thenReturn(GuildMembershipStatus.MEMBER);
        when(guildManager.getGuildMember(99, 12)).thenReturn(acceptedMember);

        GuildMember pendingMember = mock(GuildMember.class);
        when(pendingMember.getRank()).thenReturn(GuildRank.MEMBER);
        when(pendingMember.getMembershipStatus()).thenReturn(GuildMembershipStatus.PENDING);
        when(guildManager.getGuildMember(99, 20)).thenReturn(pendingMember);

        GameEnvironment gameEnvironment = mock(GameEnvironment.class);
        when(gameEnvironment.getGuildManager()).thenReturn(guildManager);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(gameEnvironment);

            assertEquals(
                    RoomRightLevels.GUILD_RIGHTS,
                    room.getRightsManager().getGuildRightLevel(habbo(12)),
                    "An accepted member of a shared-rights guild must receive GUILD_RIGHTS");
            assertEquals(
                    RoomRightLevels.NONE,
                    room.getRightsManager().getGuildRightLevel(habbo(20)),
                    "A pending (non-accepted) applicant must not receive guild rights");
        }
    }

    private static Habbo habbo(int id) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(id);
        return habbo;
    }
}
