package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.wired.WiredVariableChangeOrigin;
import org.junit.jupiter.api.Test;

/** The official origin bits (0 this room, 1 another room, 2 inspection, 3 external) against ours. */
class WiredTriggerVariableChangedOriginTest {

    @Test
    void everyOriginIsMinusOneBothWays() {
        assertEquals(-1, WiredTriggerVariableChanged.toOfficialOrigins(WiredVariableChangeOrigin.MASK_ALL));
        assertEquals(-1, WiredTriggerVariableChanged.toOfficialOrigins(0));
        assertEquals(WiredVariableChangeOrigin.MASK_ALL, WiredTriggerVariableChanged.fromOfficialOrigins(-1));
    }

    @Test
    void ourOriginsLandOnTheOfficialBits() {
        int wired = 1 << WiredVariableChangeOrigin.WIRED;
        int webApi = 1 << WiredVariableChangeOrigin.WEB_API;
        int tools = 1 << WiredVariableChangeOrigin.CREATOR_TOOLS;

        assertEquals(0b0001, WiredTriggerVariableChanged.toOfficialOrigins(wired));
        assertEquals(0b1000, WiredTriggerVariableChanged.toOfficialOrigins(webApi));
        assertEquals(0b0100, WiredTriggerVariableChanged.toOfficialOrigins(tools));
        assertEquals(wired | tools, WiredTriggerVariableChanged.fromOfficialOrigins(0b0101));
        // "Another room": a shared variable changed in another room.
        assertEquals(
                1 << WiredVariableChangeOrigin.OTHER_ROOM, WiredTriggerVariableChanged.fromOfficialOrigins(0b0010));
    }
}
