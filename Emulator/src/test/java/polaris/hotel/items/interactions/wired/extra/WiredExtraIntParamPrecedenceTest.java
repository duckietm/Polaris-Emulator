package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;

/**
 * The configuration add-ons accept their value either as the first int param or, for clients that
 * only have a text field to hand, as the string param. The int is the primary channel: the string
 * is a fallback for when no int was sent at all, never an override of an int that happens to equal
 * the current value (or 0). Otherwise a stale string typed into the dialog wins over the number the
 * user just picked.
 */
class WiredExtraIntParamPrecedenceTest {

    @Test
    void animationTimeKeepsTheIntWhenBothAreSent() throws Exception {
        WiredExtraAnimationTime extra = new WiredExtraAnimationTime(1, 1, base(), "", 0, 0);
        int current = extra.getDurationMs();

        extra.saveData(new WiredSettings(new int[] {current}, "1500", new int[0], 0), null);
        assertEquals(current, extra.getDurationMs());

        extra.saveData(new WiredSettings(new int[0], "1500", new int[0], 0), null);
        assertEquals(1500, extra.getDurationMs());
    }

    @Test
    void movementCurveKeepsTheIntWhenBothAreSent() throws Exception {
        WiredExtraMovementCurve extra = new WiredExtraMovementCurve(1, 1, base(), "", 0, 0);

        // The text is the variable token now: the curve comes from the ints alone.
        extra.saveData(
                new WiredSettings(new int[] {0, 80, 1, WiredExtraMovementCurve.CURVE_LINEAR}, "3", new int[0], 0),
                null);
        assertEquals(WiredExtraMovementCurve.CURVE_LINEAR, extra.getCurveType());

        extra.saveData(new WiredSettings(new int[0], "3", new int[0], 0), null);
        assertEquals(WiredExtraMovementCurve.CURVE_JUMP, extra.getCurveType());
    }

    @Test
    void timeUtilitiesReadsOnlyTheInts() throws Exception {
        WiredExtraTimeUtilities extra = new WiredExtraTimeUtilities(1, 1, base(), "", 0, 0);

        extra.saveData(new WiredSettings(new int[] {1 << 4, 1}, "3", new int[0], 0), null);
        assertEquals(1 << 4, extra.getSubvariableMask());
        assertEquals(WiredExtraTimeUtilities.MODE_CREATION_TIME, extra.getMode());

        extra.saveData(new WiredSettings(new int[0], "3", new int[0], 0), null);
        assertEquals(0, extra.getSubvariableMask());
        assertEquals(WiredExtraTimeUtilities.UNIT_SECONDS, extra.getTimeUnit());
    }

    @Test
    void furniFilterKeepsTheIntWhenBothAreSent() throws Exception {
        WiredExtraFilterFurni extra = new WiredExtraFilterFurni(1, 1, base(), "", 0, 0);

        extra.saveData(new WiredSettings(new int[] {0}, "7", new int[0], 0), null);
        assertEquals(0, extra.getAmount());

        extra.saveData(new WiredSettings(new int[0], "7", new int[0], 0), null);
        assertEquals(7, extra.getAmount());
    }

    @Test
    void userFilterKeepsTheIntWhenBothAreSent() throws Exception {
        WiredExtraFilterUser extra = new WiredExtraFilterUser(1, 1, base(), "", 0, 0);

        extra.saveData(new WiredSettings(new int[] {0}, "7", new int[0], 0), null);
        assertEquals(0, extra.getAmount());

        extra.saveData(new WiredSettings(new int[0], "7", new int[0], 0), null);
        assertEquals(7, extra.getAmount());
    }

    private static Item base() {
        return mock(Item.class);
    }
}
