package polaris.hotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The official variable targets on the wire against our stored holder kinds. */
class WiredVariableTargetsTest {

    @Test
    void ourKindsTravelAsTheOfficialTargetsAndBack() {
        int[] ours = {0, 1, 2, 3};
        int[] official = {1, 0, -20, -10};
        for (int i = 0; i < ours.length; i++) {
            assertEquals(official[i], WiredVariableTargets.toOfficial(ours[i]));
            assertEquals(ours[i], WiredVariableTargets.fromOfficial(official[i]));
        }
        assertEquals(0, WiredVariableTargets.fromOfficial(42), "unknown is a user");
    }
}
