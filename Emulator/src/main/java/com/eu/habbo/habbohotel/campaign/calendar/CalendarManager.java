package com.eu.habbo.habbohotel.campaign.calendar;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.events.calendar.AdventCalendarProductComposer;
import com.eu.habbo.plugin.events.users.calendar.UserClaimRewardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.Date;

import static java.time.temporal.ChronoUnit.DAYS;


public class CalendarManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarCampaign.class);

    final private static Map<Integer, CalendarCampaign> calendarCampaigns = new HashMap<>();
    public static double HC_MODIFIER;

    public CalendarManager() {
        long millis = System.currentTimeMillis();
        this.reload();
        LOGGER.info("Calendar Manager -> Loaded! ({} MS)", (System.currentTimeMillis() - millis));
    }

    public void dispose(){
        calendarCampaigns.clear();
    }

    public boolean reload() {
        this.dispose();
        try {
            SqlQueries.query(
                    "SELECT * FROM calendar_campaigns WHERE enabled = 1",
                    CalendarCampaign::new)
                    .forEach(c -> calendarCampaigns.put(c.getId(), c));
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }

        try {
            SqlQueries.query(
                    "SELECT * FROM calendar_rewards",
                    rs -> Map.entry(rs.getInt("campaign_id"), new CalendarRewardObject(rs)))
                    .forEach(entry -> {
                        CalendarCampaign campaign = calendarCampaigns.get(entry.getKey());
                        if (campaign != null) {
                            campaign.addReward(entry.getValue());
                        }
                    });
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }

        HC_MODIFIER = Emulator.getConfig().getDouble("hotel.calendar.pixels.hc_modifier", 2.0);

        return true;
    }

    public void addCampaign(CalendarCampaign campaign) {

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO calendar_campaigns ( name, image, start_timestamp, total_days, lock_expired) VALUES (?, ?, ?, ? , ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, campaign.getName());
            statement.setString(2, campaign.getImage());
            statement.setInt(3, campaign.getStartTimestamp());
            statement.setInt(4, campaign.getTotalDays());
            statement.setBoolean(5, campaign.getLockExpired());
            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating calendar campaign failed, no rows affected.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    campaign.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating calendar campaign failed, no ID found.");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        calendarCampaigns.put(campaign.getId(), campaign);
    }

    public boolean deleteCampaign(CalendarCampaign campaign) {
        calendarCampaigns.remove(campaign.getId());

        try {
            return SqlQueries.update("DELETE FROM calendar_campaigns WHERE id = ? LIMIT 1", campaign.getId()) > 0;
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }

    public CalendarCampaign getCalendarCampaign(String campaignName) {
        return calendarCampaigns.values().stream().filter(cc -> Objects.equals(cc.getName(), campaignName)).findFirst().orElse(null);
    }
    public Map<Integer, CalendarCampaign> getCalendarCampaigns() {
        return calendarCampaigns;
    }

    public void claimCalendarReward(Habbo habbo, String campaignName, int day, boolean force) {
        CalendarCampaign campaign = calendarCampaigns.values().stream().filter(cc -> Objects.equals(cc.getName(), campaignName)).findFirst().orElse(null);
        if(campaign == null) return;
        if (habbo.getHabboStats().calendarRewardsClaimed.stream().noneMatch(claimed -> claimed.getCampaignId() == campaign.getId() && claimed.getDay() == day)) {

            Set<Integer> keys = campaign.getRewards().keySet();
            Map<Integer, Integer> rewards = new HashMap<>();
            if(keys.isEmpty()) return;
            keys.forEach(key -> rewards.put(rewards.size() + 1, key));
            int rand = Emulator.getRandom().nextInt(rewards.size() - 1 + 1) + 1;
            int random = rewards.get(rand);
            CalendarRewardObject object = campaign.getRewards().get(random);
            if (object == null) return;
                int daysBetween = (int) DAYS.between(new Timestamp(campaign.getStartTimestamp() * 1000L).toInstant(), new Date().toInstant());
            if(daysBetween >= 0 && daysBetween <= campaign.getTotalDays()) {
                int diff = (daysBetween - day);
                if ((((diff <= 2 || !campaign.getLockExpired()) && diff >= 0) || (force && habbo.hasPermission("acc_calendar_force")))) {

                    if (Emulator.getPluginManager().fireEvent(new UserClaimRewardEvent(habbo, campaign, day, object, force)).isCancelled()) {
                        return;
                    }

                    habbo.getHabboStats().calendarRewardsClaimed.add(new CalendarRewardClaimed(habbo.getHabboInfo().getId(), campaign.getId(), day, object.getId(), new Timestamp(System.currentTimeMillis())));
                    habbo.getClient().sendResponse(new AdventCalendarProductComposer(true, object, habbo));
                    object.give(habbo);
                    try {
                        SqlQueries.update(
                                "INSERT INTO calendar_rewards_claimed (user_id, campaign_id, day, reward_id, timestamp) VALUES (?, ?, ?, ?, ?)",
                                habbo.getHabboInfo().getId(),
                                campaign.getId(),
                                day,
                                object.getId(),
                                Emulator.getIntUnixTimestamp());
                    } catch (SqlQueries.DataAccessException e) {
                        LOGGER.error("Caught SQL exception", e);
                    }
                }
            }
        }
    }
}

