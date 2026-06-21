package com.eu.habbo.messages.outgoing.events.calendar;

import com.eu.habbo.habbohotel.campaign.calendar.CalendarRewardClaimed;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class AdventCalendarDataComposer extends MessageComposer {
    private final String eventName;
    private final String campaignImage;
    private final int totalDays;
    private final int currentDay;
    private final ArrayList<CalendarRewardClaimed> unlocked;
    private final boolean lockExpired;

    public AdventCalendarDataComposer(String eventName, String campaignImage, int totalDays, int currentDay, ArrayList<CalendarRewardClaimed> unlocked, boolean lockExpired) {
        this.eventName = eventName;
        this.campaignImage = campaignImage;
        this.totalDays = totalDays;
        this.currentDay = currentDay;
        this.unlocked = unlocked;
        this.lockExpired = lockExpired;
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
        if (this.lockExpired) { for (int i = 0; i < this.totalDays; i++) {
            expired.add(i);
        }
        }
        expired.remove(this.currentDay);
        if(this.currentDay > 1) expired.remove(this.currentDay - 2);
        if(this.currentDay > 0) expired.remove(this.currentDay - 1);

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
