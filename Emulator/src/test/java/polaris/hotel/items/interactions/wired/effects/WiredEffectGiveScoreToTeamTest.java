package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import org.junit.jupiter.api.Test;

/**
 * Gson answers null for a team name it does not know, and the dialog serializer then read the
 * team's type off it. An unknown team is the default one.
 */
class WiredEffectGiveScoreToTeamTest {

    @Test
    void aTeamNameThatNamesNothingLoadsAsRed() throws Exception {
        WiredEffectGiveScoreToTeam box = new WiredEffectGiveScoreToTeam(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("{\"score\":5,\"operation\":0,\"team\":\"PURPLE\",\"delay\":0}"), null);

        assertEquals("RED", json(box).get("team").getAsString());
        assertEquals(5, json(box).get("score").getAsInt());
    }
}
