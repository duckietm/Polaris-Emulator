package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.games.Game;
import polaris.hotel.games.GamePlayer;
import polaris.hotel.games.GameTeam;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * Gson answers null for a team name it does not know, and the dialog serializer then read the
 * team's type off it. An unknown team is the default one.
 */
class WiredEffectJoinTeamTest {

    @Test
    void aTeamNameThatNamesNothingLoadsAsRed() throws Exception {
        WiredEffectJoinTeam box = new WiredEffectJoinTeam(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("{\"team\":\"PURPLE\",\"teamType\":0,\"delay\":0,\"userSource\":0}"), null);

        assertEquals("RED", json(box).get("team").getAsString());
    }

    @Test
    void aKnownTeamStillLoads() throws Exception {
        WiredEffectJoinTeam box = new WiredEffectJoinTeam(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("{\"team\":\"BLUE\",\"teamType\":0,\"delay\":0,\"userSource\":0}"), null);

        assertEquals("BLUE", json(box).get("team").getAsString());
    }

    private static GameTeam team(GameTeamColors color, int members) {
        GameTeam team = mock(GameTeam.class);
        Set<GamePlayer> players = new HashSet<>();
        for (int i = 0; i < members; i++) players.add(mock(GamePlayer.class));
        when(team.getMembers()).thenReturn(players);
        return team;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static User playerIn(Game game, GameTeamColors color) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        GamePlayer player = mock(GamePlayer.class);
        when(player.getTeamColor()).thenReturn(color);
        when(user.getUserInfo()).thenReturn(info);
        if (color != null) {
            when(info.getGamePlayer()).thenReturn(player);
            when(info.getCurrentGame()).thenReturn((Class) game.getClass());
        }
        return user;
    }

    @Test
    void theSmallestTeamIsTheOneWithFewestOthersInColourOrder() {
        Game game = mock(Game.class);
        GameTeam red = team(GameTeamColors.RED, 2);
        GameTeam green = team(GameTeamColors.GREEN, 1);
        GameTeam yellow = team(GameTeamColors.YELLOW, 1);
        when(game.getTeam(GameTeamColors.RED)).thenReturn(red);
        when(game.getTeam(GameTeamColors.GREEN)).thenReturn(green);
        when(game.getTeam(GameTeamColors.YELLOW)).thenReturn(yellow);

        // Blue has nobody yet, so a newcomer goes there.
        assertEquals(GameTeamColors.BLUE, WiredEffectJoinTeam.smallestTeam(game, playerIn(game, null)));
        // The only yellow player already has the smallest team to themselves and stays.
        assertEquals(
                GameTeamColors.YELLOW, WiredEffectJoinTeam.smallestTeam(game, playerIn(game, GameTeamColors.YELLOW)));
        // No game yet: every team is empty and red comes first.
        assertEquals(GameTeamColors.RED, WiredEffectJoinTeam.smallestTeam(null, playerIn(game, null)));
    }

    @Test
    void randomPicksOneOfTheFourTeams() throws Exception {
        WiredEffectJoinTeam box = new WiredEffectJoinTeam(1, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"team\":\"BLUE\",\"teamType\":0,\"delay\":0,\"userSource\":0,\"joinMode\":2}"), null);

        for (int i = 0; i < 20; i++) {
            GameTeamColors team = box.resolveTeam(null, playerIn(mock(Game.class), null));
            assertTrue(team.type >= GameTeamColors.RED.type && team.type <= GameTeamColors.YELLOW.type);
        }
    }

    @Test
    void theJoinModeIsSavedClampedAndOldBoxesJoinTheChosenTeam() throws Exception {
        Room room = mock(Room.class);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            WiredEffectTestFixtures.installHotel(emulator, room);
            WiredEffectJoinTeam box = new WiredEffectJoinTeam(1, 1, base(), "", 0, 0);

            box.saveData(
                    new WiredSettings(new int[] {3, 1, WiredEffectJoinTeam.MODE_SMALLEST}, "", new int[0], 0), null);
            assertEquals(
                    WiredEffectJoinTeam.MODE_SMALLEST, json(box).get("joinMode").getAsInt());
            assertEquals("BLUE", json(box).get("team").getAsString());

            // Official layout: [team, team type]; ours follows: [join mode].
            box.saveData(new WiredSettings(new int[] {3, 1, 9}, "", new int[0], 0), null);
            assertEquals(
                    WiredEffectJoinTeam.MODE_CHOSEN, json(box).get("joinMode").getAsInt());

            box.saveData(new WiredSettings(new int[] {2, 0}, "", new int[0], 0), null);
            assertEquals(
                    WiredEffectJoinTeam.MODE_CHOSEN, json(box).get("joinMode").getAsInt());
            assertEquals("GREEN", json(box).get("team").getAsString());
        }

        WiredEffectJoinTeam old = new WiredEffectJoinTeam(1, 1, base(), "", 0, 0);
        old.loadWiredData(row("{\"team\":\"GREEN\",\"teamType\":0,\"delay\":0,\"userSource\":0}"), null);
        assertEquals(GameTeamColors.GREEN, old.resolveTeam(null, playerIn(mock(Game.class), null)));
    }
}
