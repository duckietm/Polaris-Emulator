package polaris.hotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.body;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.boxBase;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.room;

import org.junit.jupiter.api.Test;
import polaris.hotel.wired.WiredTriggerType;

/**
 * The team-result triggers inherit "game starts" serialization. The code written last in the body is
 * what the client picks a dialog by, so it has to be the subclass's own code, not the parent's.
 */
class WiredTriggerTeamResultSerializeTest {

    @Test
    void teamWinsAdvertisesTheTeamResultCode() {
        WiredTriggerTeamWins trigger = new WiredTriggerTeamWins(1, 1, boxBase(), "", 0, 0);

        assertEquals(
                WiredTriggerType.TEAM_GAME_RESULT.code, body(trigger, room(1)).typeCode());
    }

    @Test
    void teamLosesAdvertisesTheTeamResultCode() {
        WiredTriggerTeamLoses trigger = new WiredTriggerTeamLoses(2, 1, boxBase(), "", 0, 0);

        assertEquals(
                WiredTriggerType.TEAM_GAME_RESULT.code, body(trigger, room(1)).typeCode());
    }

    @Test
    void gameStartsStillAdvertisesItsOwnCode() {
        WiredTriggerGameStarts trigger = new WiredTriggerGameStarts(3, 1, boxBase(), "", 0, 0);

        assertEquals(WiredTriggerType.GAME_STARTS.code, body(trigger, room(1)).typeCode());
    }
}
