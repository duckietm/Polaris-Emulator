package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RoomPromotionManagerEndTest {
    @Test
    void endingWithoutARunningPromotionClearsTheFlagAndReportsNothingToBroadcast() {
        RoomPromotionManager manager = new RoomPromotionManager(null, () -> 42);
        manager.setPromoted(true);

        assertFalse(manager.endPromotion(1_700_000_000));
        assertFalse(manager.getPromotedFlag());
        assertNull(manager.getPromotion());
    }
}
