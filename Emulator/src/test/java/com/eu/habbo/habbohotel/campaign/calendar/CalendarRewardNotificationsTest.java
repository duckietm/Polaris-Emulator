package com.eu.habbo.habbohotel.campaign.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** One bubble for each part of an opened calendar day, typed by kind so the client can word it and pick the icon. */
class CalendarRewardNotificationsTest {

    private static CalendarRewardObject reward(Map<String, Integer> ints, Map<String, String> strings)
            throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString(anyString())).thenReturn("");
        for (Map.Entry<String, Integer> entry : ints.entrySet())
            when(set.getInt(entry.getKey())).thenReturn(entry.getValue());
        for (Map.Entry<String, String> entry : strings.entrySet())
            when(set.getString(entry.getKey())).thenReturn(entry.getValue());
        return new CalendarRewardObject(set);
    }

    @Test
    void sendsOneBubblePerPartInGivingOrder() throws SQLException {
        CalendarRewardObject reward = reward(
                Map.of("credits", 50, "pixels", 100, "points", 5, "points_type", 5, "subscription_days", 7),
                Map.of("subscription_type", "HABBO_CLUB", "badge", "ADM"));

        List<CalendarRewardNotifications.Bubble> bubbles = CalendarRewardNotifications.bubbles(reward, 200, "throne");

        assertEquals(
                List.of(
                        "calendar.reward.credits",
                        "calendar.reward.pixels",
                        "calendar.reward.points",
                        "calendar.reward.club",
                        "calendar.reward.furni"),
                bubbles.stream().map(CalendarRewardNotifications.Bubble::type).toList());
        assertTrue(bubbles.stream().allMatch(b -> "BUBBLE".equals(b.keys().get("display"))));
        assertEquals(
                Map.of("display", "BUBBLE", "amount", "50", "currency", "-1"),
                bubbles.get(0).keys());
        // The duckets actually given, after the HC modifier.
        assertEquals("200", bubbles.get(1).keys().get("amount"));
        assertEquals("0", bubbles.get(1).keys().get("currency"));
        assertEquals("5", bubbles.get(2).keys().get("currency"));
        assertEquals("7", bubbles.get(3).keys().get("amount"));
        assertEquals("HABBO_CLUB", bubbles.get(3).keys().get("subscription"));
        assertEquals("throne", bubbles.get(4).keys().get("furni"));
    }

    @Test
    void sendsNothingForABadgeOnlyReward() throws SQLException {
        // Adding the badge already sends the received-badge bubble.
        assertTrue(CalendarRewardNotifications.bubbles(reward(Map.of(), Map.of("badge", "ADM")), 0, null)
                .isEmpty());
    }
}
