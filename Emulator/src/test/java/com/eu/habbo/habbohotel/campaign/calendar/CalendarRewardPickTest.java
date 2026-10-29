package com.eu.habbo.habbohotel.campaign.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Calendar rewards per day: calendar_rewards.day (from 1) ties a reward to a day, NULL keeps it in the random pool. */
class CalendarRewardPickTest {

    private static CalendarRewardObject reward(int id, int day) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString(anyString())).thenReturn("");
        when(set.getInt("id")).thenReturn(id);
        when(set.getInt("day")).thenReturn(day);
        return new CalendarRewardObject(set);
    }

    @Test
    void readsANullDayAsAnyDay() throws SQLException {
        assertEquals(0, reward(1, 0).getDay());
        assertEquals(3, reward(2, 3).getDay());
    }

    @Test
    void givesTheRewardOfTheOpenedDay() throws SQLException {
        List<CalendarRewardObject> rewards = List.of(reward(1, 1), reward(2, 2), reward(3, 0));

        // The client counts days from 0: day 0 is the reward with day = 1.
        assertEquals(1, CalendarManager.pickReward(rewards, 0, n -> 0).getId());
        assertEquals(2, CalendarManager.pickReward(rewards, 1, n -> 0).getId());
    }

    @Test
    void picksAmongSeveralRewardsOfTheSameDayOnly() throws SQLException {
        List<CalendarRewardObject> rewards = List.of(reward(5, 2), reward(4, 2), reward(6, 0));

        assertEquals(4, CalendarManager.pickReward(rewards, 1, n -> 0).getId());
        assertEquals(
                5,
                CalendarManager.pickReward(rewards, 1, n -> {
                            assertEquals(2, n);
                            return 1;
                        })
                        .getId());
    }

    @Test
    void fallsBackToTheRewardsWithoutADay() throws SQLException {
        List<CalendarRewardObject> rewards = List.of(reward(1, 1), reward(7, 0), reward(8, 0));

        assertEquals(
                8,
                CalendarManager.pickReward(rewards, 4, n -> {
                            assertEquals(2, n);
                            return 1;
                        })
                        .getId());
    }

    @Test
    void givesNothingWhenTheDayHasNoRewardAndThereIsNoPool() throws SQLException {
        assertNull(CalendarManager.pickReward(List.of(reward(1, 1)), 4, n -> 0));
        assertNull(CalendarManager.pickReward(List.of(), 0, n -> 0));
    }
}
