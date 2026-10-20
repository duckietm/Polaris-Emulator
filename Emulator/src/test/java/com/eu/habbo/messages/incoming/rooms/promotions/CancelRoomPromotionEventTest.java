package com.eu.habbo.messages.incoming.rooms.promotions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CancelRoomPromotionEventTest {
    @Test
    void cancelsOnlyTheEventOfTheCurrentRoom() {
        assertTrue(CancelRoomPromotionEvent.mayCancel(42, 42));
        assertFalse(CancelRoomPromotionEvent.mayCancel(42, 43));
        assertFalse(CancelRoomPromotionEvent.mayCancel(0, 0));
        assertFalse(CancelRoomPromotionEvent.mayCancel(42, -1));
    }
}
