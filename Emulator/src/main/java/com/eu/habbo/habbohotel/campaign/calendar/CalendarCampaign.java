package com.eu.habbo.habbohotel.campaign.calendar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class CalendarCampaign {
    private int id;
    private final String name;
    private final String image;
    private Map<Integer , CalendarRewardObject> rewards = new HashMap<>();
    private final Integer start_timestamp;
    private final int total_days;
    private final boolean lock_expired;

    public CalendarCampaign(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.image = set.getString("image");
        this.start_timestamp = set.getInt("start_timestamp");
        this.total_days = set.getInt("total_days");
        this.lock_expired = set.getInt("lock_expired") == 1;
    }

    public CalendarCampaign(int id, String name, String image, Integer start_timestamp, int total_days, boolean lock_expired) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.start_timestamp = start_timestamp;
        this.total_days = total_days;
        this.lock_expired = lock_expired;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getImage() {
        return this.image;
    }

    public Integer getStartTimestamp() {
        return this.start_timestamp;
    }

    public int getTotalDays() { return this.total_days; }

    public boolean getLockExpired() { return this.lock_expired; }

    public Map<Integer, CalendarRewardObject> getRewards() { return rewards; }

    public void setId(int id) { this.id = id; }

    public void setRewards(Map<Integer, CalendarRewardObject> rewards) { this.rewards = rewards; }

    public void addReward(CalendarRewardObject reward) { this.rewards.put(reward.getId(), reward); }
}
