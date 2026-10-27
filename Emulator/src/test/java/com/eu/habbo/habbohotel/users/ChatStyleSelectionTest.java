package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** AIR chatstyles_xml: what the chat style selector offers a player. */
class ChatStyleSelectionTest {

    @Test
    void plainStylesAreForEveryone() {
        for (int style : new int[] {0, 3, 4, 5, 6, 7, 23}) {
            assertTrue(ChatStyleSelection.isSelectable(style, false, false), "style " + style);
        }
    }

    @Test
    void clubStylesNeedHabboClub() {
        for (int style : new int[] {9, 10, 11, 17, 19, 22, 24, 27, 29}) {
            assertFalse(ChatStyleSelection.isSelectable(style, false, false), "style " + style);
            assertTrue(ChatStyleSelection.isSelectable(style, true, false), "style " + style);
        }
    }

    @Test
    void systemStylesAndUnknownIdsAreNeverOffered() {
        for (int style : new int[] {1, 2, 8, 18, 28, 30, 31, 32, 33, 34, 35, 36, 38, 39, 120, 130, 200, 253, -1}) {
            assertFalse(ChatStyleSelection.isSelectable(style, true, true), "style " + style);
        }
    }

    @Test
    void theAmbassadorStyleIsForAmbassadors() {
        assertFalse(ChatStyleSelection.isSelectable(37, true, false));
        assertTrue(ChatStyleSelection.isSelectable(37, false, true));
    }
}
