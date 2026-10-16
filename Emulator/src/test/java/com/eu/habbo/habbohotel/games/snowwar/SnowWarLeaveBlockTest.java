package com.eu.habbo.habbohotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnowWarLeaveBlockTest {

    @Test
    void aLeaveBlockCountsDownAndAZeroBlockDoesNothing() {
        SnowWarManager manager = SnowWarManager.getInstance();
        int userId = 987_654_321;

        assertEquals(0, manager.getBlockSecondsLeft(userId));

        manager.blockUser(userId, 0);
        assertEquals(0, manager.getBlockSecondsLeft(userId));

        manager.blockUser(userId, 180);
        int left = manager.getBlockSecondsLeft(userId);
        assertTrue(left > 178 && left <= 180, "left=" + left);

        manager.blockUser(userId, -1);
        assertTrue(manager.getBlockSecondsLeft(userId) > 0, "a non-positive block keeps the running one");
    }
}
