package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RankLimitsTest {
    @Test
    void aRankValueRaisesTheHotelSetting() {
        Rank vip = new Rank(2);
        vip.setLimits(200, 1500, 60);

        assertEquals(200, RankLimits.raise(25, vip, RankLimits.Limit.ROOMS));
        assertEquals(1500, RankLimits.raise(300, vip, RankLimits.Limit.FRIENDS));
        assertEquals(60, RankLimits.raise(30, vip, RankLimits.Limit.FAVOURITE_ROOMS));
    }

    @Test
    void itNeverLowersIt() {
        Rank small = new Rank(3);
        small.setLimits(10, 0, 0);

        assertEquals(35, RankLimits.raise(35, small, RankLimits.Limit.ROOMS), "a club member keeps the club limit");
        assertEquals(300, RankLimits.raise(300, small, RankLimits.Limit.FRIENDS), "0 is the hotel setting");
        assertEquals(30, RankLimits.raise(30, null, RankLimits.Limit.FAVOURITE_ROOMS));
    }
}
