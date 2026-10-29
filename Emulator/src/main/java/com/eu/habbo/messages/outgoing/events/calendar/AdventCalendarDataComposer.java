package com.eu.habbo.messages.outgoing.events.calendar;

import com.eu.habbo.habbohotel.campaign.calendar.CalendarRewardClaimed;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarRewardObject;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdventCalendarDataComposer extends MessageComposer {
    private final String eventName;
    private final String campaignImage;
    private final int totalDays;
    private final int currentDay;
    private final ArrayList<CalendarRewardClaimed> unlocked;
    private final boolean lockExpired;
    private final Map<Integer, CalendarRewardObject> rewardsById;
    private final Habbo habbo;

    public AdventCalendarDataComposer(
            String eventName,
            String campaignImage,
            int totalDays,
            int currentDay,
            ArrayList<CalendarRewardClaimed> unlocked,
            boolean lockExpired) {
        this.eventName = eventName;
        this.campaignImage = campaignImage;
        this.totalDays = totalDays;
        this.currentDay = currentDay;
        this.unlocked = unlocked;
        this.lockExpired = lockExpired;
        this.rewardsById = null;
        this.habbo = null;
    }

    /**
     * Also sends what every opened day of the campaign held (Octane extension after AIR's fields), so the opened
     * boxes can show their reward after a new login.
     */
    public AdventCalendarDataComposer(
            String eventName,
            String campaignImage,
            int totalDays,
            int currentDay,
            ArrayList<CalendarRewardClaimed> unlocked,
            boolean lockExpired,
            Map<Integer, CalendarRewardObject> rewardsById,
            Habbo habbo) {
        this.eventName = eventName;
        this.campaignImage = campaignImage;
        this.totalDays = totalDays;
        this.currentDay = currentDay;
        this.unlocked = unlocked;
        this.lockExpired = lockExpired;
        this.rewardsById = rewardsById;
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AdventCalendarDataComposer);
        this.response.appendString(this.eventName);
        this.response.appendString(this.campaignImage);
        this.response.appendInt(this.currentDay);
        this.response.appendInt(this.totalDays);
        this.response.appendInt(this.unlocked.size());

        Set<Integer> expired = new LinkedHashSet<>();
        if (this.lockExpired) {
            for (int i = 0; i < this.totalDays; i++) {
                expired.add(i);
            }
        }
        expired.remove(this.currentDay);
        if (this.currentDay > 1) expired.remove(this.currentDay - 2);
        if (this.currentDay > 0) expired.remove(this.currentDay - 1);

        this.unlocked.forEach(claimed -> {
            AdventCalendarDataComposer.this.response.appendInt(claimed.getDay());
            expired.remove(claimed.getDay());
        });

        if (this.lockExpired) {
            this.response.appendInt(expired.size());
            for (int day : expired) {
                this.response.appendInt(day);
            }
        } else {
            this.response.appendInt(0);
        }

        if (this.rewardsById != null) {
            List<CalendarRewardClaimed> opened = this.unlocked.stream()
                    .filter(claimed -> this.rewardsById.containsKey(claimed.getRewardId()))
                    .toList();
            this.response.appendInt(opened.size());
            for (CalendarRewardClaimed claimed : opened) {
                this.response.appendInt(claimed.getDay());
                AdventCalendarProductComposer.appendReward(
                        this.response, this.rewardsById.get(claimed.getRewardId()), this.habbo);
            }
        }

        return this.response;
    }

    public String getEventName() {
        return eventName;
    }

    public String getCampaignImage() {
        return campaignImage;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public int getCurrentDay() {
        return currentDay;
    }

    public ArrayList<CalendarRewardClaimed> getUnlocked() {
        return unlocked;
    }

    public boolean isLockExpired() {
        return lockExpired;
    }
}
