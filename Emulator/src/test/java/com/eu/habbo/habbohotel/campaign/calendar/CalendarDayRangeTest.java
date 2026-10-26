package com.eu.habbo.habbohotel.campaign.calendar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The day arrives straight off the advent-calendar packet. A negative one used to
 * pass the window check (daysBetween - day is then larger, never negative) and, as
 * every (campaign, day) pair is distinct, the already-claimed check never fired:
 * day = -1, -2, -3 ... was one random reward per packet.
 */
class CalendarDayRangeTest {

    private static CalendarCampaign campaignOf(int totalDays) {
        return new CalendarCampaign(1, "xmas", "img", 0, totalDays, false);
    }

    @Test
    void acceptsTheDaysTheCalendarRenders() {
        CalendarCampaign campaign = campaignOf(24);
        assertTrue(CalendarManager.isDayInCampaign(campaign, 0));
        assertTrue(CalendarManager.isDayInCampaign(campaign, 12));
        assertTrue(CalendarManager.isDayInCampaign(campaign, 23));
    }

    @Test
    void rejectsADayBeforeTheCampaign() {
        CalendarCampaign campaign = campaignOf(24);
        assertFalse(CalendarManager.isDayInCampaign(campaign, -1));
        assertFalse(CalendarManager.isDayInCampaign(campaign, -2));
        assertFalse(CalendarManager.isDayInCampaign(campaign, Integer.MIN_VALUE));
    }

    @Test
    void rejectsADayPastTheCampaign() {
        CalendarCampaign campaign = campaignOf(24);
        assertFalse(CalendarManager.isDayInCampaign(campaign, 24));
        assertFalse(CalendarManager.isDayInCampaign(campaign, Integer.MAX_VALUE));
    }

    @Test
    void rejectsEveryDayOfAnUnknownCampaign() {
        assertFalse(CalendarManager.isDayInCampaign(null, 0));
        assertFalse(CalendarManager.isDayInCampaign(campaignOf(0), 0));
    }
}
