package polaris.hotel.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Where a variable change came from, so the "variable changed" trigger can listen to some sources
 * and ignore others: a wired box in this room, the web API, or the creator tools. The origin travels with the thread that performs the write, and a
 * write that nobody labelled is a wired write, because the wired engine is the one caller that
 * cannot be asked to label itself at every site.
 */
class WiredVariableChangeOriginTest {

    @Test
    void anUnlabelledWriteIsAWiredWrite() {
        assertEquals(WiredVariableChangeOrigin.WIRED, WiredVariableChangeOrigin.current());
    }

    @Test
    void aLabelHoldsForTheScopeAndIsGoneAfterwards() {
        int[] seen = new int[1];

        WiredVariableChangeOrigin.run(
                WiredVariableChangeOrigin.WEB_API, () -> seen[0] = WiredVariableChangeOrigin.current());

        assertEquals(WiredVariableChangeOrigin.WEB_API, seen[0]);
        assertEquals(WiredVariableChangeOrigin.WIRED, WiredVariableChangeOrigin.current());
    }

    @Test
    void aNestedLabelRestoresTheOuterOne() {
        int[] seen = new int[2];

        WiredVariableChangeOrigin.run(WiredVariableChangeOrigin.CREATOR_TOOLS, () -> {
            WiredVariableChangeOrigin.run(
                    WiredVariableChangeOrigin.WEB_API, () -> seen[0] = WiredVariableChangeOrigin.current());
            seen[1] = WiredVariableChangeOrigin.current();
        });

        assertEquals(WiredVariableChangeOrigin.WEB_API, seen[0]);
        assertEquals(WiredVariableChangeOrigin.CREATOR_TOOLS, seen[1]);
    }

    @Test
    void theLabelIsGoneEvenWhenTheScopeThrows() {
        try {
            WiredVariableChangeOrigin.run(WiredVariableChangeOrigin.WEB_API, () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // the scope must still have been closed
        }

        assertEquals(WiredVariableChangeOrigin.WIRED, WiredVariableChangeOrigin.current());
    }

    @Test
    void theMaskListsTheOriginsATriggerListensTo() {
        int wiredAndTools = (1 << WiredVariableChangeOrigin.WIRED) | (1 << WiredVariableChangeOrigin.CREATOR_TOOLS);

        assertTrue(WiredVariableChangeOrigin.accepts(wiredAndTools, WiredVariableChangeOrigin.WIRED));
        assertTrue(WiredVariableChangeOrigin.accepts(wiredAndTools, WiredVariableChangeOrigin.CREATOR_TOOLS));
        assertFalse(WiredVariableChangeOrigin.accepts(wiredAndTools, WiredVariableChangeOrigin.WEB_API));
    }

    @Test
    void everyOriginIsAcceptedByTheAllMaskAndByABoxSavedBeforeTheMaskExisted() {
        for (int origin = WiredVariableChangeOrigin.WIRED;
                origin <= WiredVariableChangeOrigin.CREATOR_TOOLS;
                origin++) {
            assertTrue(
                    WiredVariableChangeOrigin.accepts(WiredVariableChangeOrigin.MASK_ALL, origin), "origin " + origin);
            assertTrue(WiredVariableChangeOrigin.accepts(0, origin), "origin " + origin);
        }
    }

    @Test
    void anOriginOutsideTheThreeIsTreatedAsWired() {
        assertEquals(WiredVariableChangeOrigin.WIRED, WiredVariableChangeOrigin.normalize(9));
        assertEquals(WiredVariableChangeOrigin.WIRED, WiredVariableChangeOrigin.normalize(-2));
        assertEquals(WiredVariableChangeOrigin.CREATOR_TOOLS, WiredVariableChangeOrigin.normalize(2));
    }
}
