package com.eu.habbo.habbohotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BadgeRanksTest {

    @Test
    void ranksLikeTheLeaderboardMostBadgesThenLowestUserId() {
        Map<Integer, Integer> ranks =
                BadgeRanks.rank(List.of(new int[] {7, 3}, new int[] {2, 10}, new int[] {5, 3}, new int[] {9, 0}));

        assertEquals(1, ranks.get(2));
        assertEquals(2, ranks.get(5));
        assertEquals(3, ranks.get(7));
        assertFalse(ranks.containsKey(9));
    }
}
