package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.games.GameState;
import polaris.hotel.games.GameTeam;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.games.freeze.FreezeGame;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

/** A named team's score and rank are the room's game's, with or without a triggering user. */
class WiredConditionTeamRoomLevelTest {

    private static ResultSet row(String json) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(json);
        return set;
    }

    private static Room roomWithScores(int red, int blue) {
        Room room = mock(Room.class);
        FreezeGame game = mock(FreezeGame.class);
        GameTeam redTeam = mock(GameTeam.class);
        GameTeam blueTeam = mock(GameTeam.class);
        when(redTeam.getTotalScore()).thenReturn(red);
        when(blueTeam.getTotalScore()).thenReturn(blue);
        when(game.getState()).thenReturn(GameState.RUNNING);
        when(game.getTeam(GameTeamColors.RED)).thenReturn(redTeam);
        when(game.getTeam(GameTeamColors.BLUE)).thenReturn(blueTeam);
        when(room.getGames()).thenReturn(Set.of(game));
        return room;
    }

    // No actor: a timer, or a game ending.
    private static WiredContext nobody(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
    }

    @Test
    void theNamedTeamsScoreIsCheckedWithoutAUser() throws Exception {
        Room room = roomWithScores(30, 10);
        WiredConditionTeamHasScore box = new WiredConditionTeamHasScore(1, 1, mock(Item.class), "", 0, 0);
        box.loadWiredData(
                row("{\"teamType\":" + GameTeamColors.RED.type
                        + ",\"comparison\":2,\"score\":20,\"userSource\":0,\"quantifier\":0}"),
                room);

        assertTrue(box.evaluate(nobody(room)));
        assertFalse(box.evaluate(nobody(roomWithScores(5, 10))));
    }

    @Test
    void theNamedTeamsRankIsCheckedWithoutAUser() throws Exception {
        Room room = roomWithScores(30, 10);
        WiredConditionTeamHasRank box = new WiredConditionTeamHasRank(1, 1, mock(Item.class), "", 0, 0);
        box.loadWiredData(
                row("{\"teamType\":" + GameTeamColors.BLUE.type
                        + ",\"placement\":2,\"userSource\":0,\"quantifier\":0}"),
                room);

        assertTrue(box.evaluate(nobody(room)));
    }

    @Test
    void withNoGameRunningNeitherPasses() throws Exception {
        Room room = mock(Room.class);
        when(room.getGames()).thenReturn(Set.of());
        WiredConditionTeamHasScore box = new WiredConditionTeamHasScore(1, 1, mock(Item.class), "", 0, 0);

        assertFalse(box.evaluate(nobody(room)));
    }
}
