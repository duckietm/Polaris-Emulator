package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildManager;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildMembershipStatus;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

class RoomRightsManagerGuildContractTest {
    private static final Pattern ACCEPTED_MEMBER_GUILD_RIGHTS_GUARD = Pattern.compile(
            "member\\.getMembershipStatus\\(\\)\\s*==\\s*GuildMembershipStatus\\.MEMBER\\s*&&\\s*guild\\.getRights\\(\\)",
            Pattern.MULTILINE);

    @Test
    void guildRoomRightsRequireAcceptedMembership() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/hotel/rooms/RoomRightsManager.java"), StandardCharsets.UTF_8);

        assertTrue(
                ACCEPTED_MEMBER_GUILD_RIGHTS_GUARD.matcher(source).find(),
                "Guild room rights must only apply to accepted guild members.");
        assertFalse(
                source.contains("if (guild.getRights())"),
                "A guild room with shared rights must not grant GUILD_RIGHTS to non-members.");
    }

    @Test
    void legacyRoomGuildRightPathUsesSameMembershipGuard() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/polaris/hotel/rooms/Room.java"), StandardCharsets.UTF_8);

        assertTrue(
                source.contains("return this.rightsManager.getGuildRightLevel(user);"),
                "The legacy Room path must delegate to the characterized rights policy.");
    }

    @Test
    void guildRightsAreGrantedOnlyToAcceptedMembersOfASharedRightsGuild() {
        // Executable counterpart to the source-text guard above: a MEMBER of a shared-rights guild
        // gets GUILD_RIGHTS, but a user who has only requested membership (PENDING) gets NONE.
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
                    room.getRightsManager().getGuildRightLevel(user(12)),
                    "An accepted member of a shared-rights guild must receive GUILD_RIGHTS");
            assertEquals(
                    RoomRightLevels.NONE,
                    room.getRightsManager().getGuildRightLevel(user(20)),
                    "A pending (non-accepted) applicant must not receive guild rights");
        }
    }

    private static User user(int id) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getId()).thenReturn(id);
        return user;
    }
}
