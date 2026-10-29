package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.wired.WiredGiveRewardItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Give Reward rolled 0-100 and matched each range with both ends included: a reward at p% won p+1 values out of
 * 101, and one at 0% still won the value its range started on. The limit was checked as limit - given == 0.
 */
class WiredRewardRollTest {

    private static final WiredGiveRewardItem NEVER = new WiredGiveRewardItem(1, true, "NEVER", 0);
    private static final WiredGiveRewardItem QUARTER = new WiredGiveRewardItem(2, true, "QUARTER", 25);
    private static final WiredGiveRewardItem HALF = new WiredGiveRewardItem(3, true, "HALF", 50);

    @Test
    void eachRewardWinsExactlyAsManyRollsAsItsProbability() {
        List<WiredGiveRewardItem> rewards = List.of(NEVER, QUARTER, HALF);
        int quarter = 0;
        int half = 0;
        int nothing = 0;
        for (int roll = 0; roll < 100; roll++) {
            WiredGiveRewardItem picked = WiredManager.pickReward(rewards, roll);
            if (picked == QUARTER) quarter++;
            else if (picked == HALF) half++;
            else if (picked == null) nothing++;
        }

        assertTrue(quarter == 25 && half == 50 && nothing == 25, quarter + "/" + half + "/" + nothing);
    }

    @Test
    void aRewardAtZeroPercentIsNeverGiven() {
        assertSame(QUARTER, WiredManager.pickReward(List.of(NEVER, QUARTER), 0));
        assertNull(WiredManager.pickReward(List.of(NEVER), 0));
    }

    @Test
    void theLimitIsReachedAtAndPastIt() {
        assertFalse(WiredManager.isLimitReached(0, 50), "no limit");
        assertFalse(WiredManager.isLimitReached(3, 2));
        assertTrue(WiredManager.isLimitReached(3, 3));
        assertTrue(WiredManager.isLimitReached(3, 4), "a count past the limit is spent too");
    }
}
