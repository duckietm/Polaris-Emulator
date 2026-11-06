package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import org.junit.jupiter.api.Test;

/** A legacy "leave team" row is the bare delay; one that is not a number used to fail the furni load. */
class WiredEffectLeaveTeamTest {

    @Test
    void aLegacyRowThatIsNotANumberLoadsWithNoDelay() throws Exception {
        WiredEffectLeaveTeam box = new WiredEffectLeaveTeam(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("abc"), null);
        assertEquals(0, box.getDelay());

        box.loadWiredData(row("7"), null);
        assertEquals(7, box.getDelay());
    }
}
