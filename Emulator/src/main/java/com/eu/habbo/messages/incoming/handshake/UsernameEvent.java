package com.eu.habbo.messages.incoming.handshake;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarCampaign;
import com.eu.habbo.habbohotel.catalog.TargetOffer;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.TargetedOfferComposer;
import com.eu.habbo.messages.outgoing.events.calendar.AdventCalendarDataComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NuxAlertComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;

import static java.time.temporal.ChronoUnit.DAYS;

public class UsernameEvent extends MessageHandler {
    public static void completeLogin(GameClient client) throws Exception {
        UsernameEvent event = new UsernameEvent();
        event.client = client;
        event.handle();
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().getHabboStats().getAchievementProgress().containsKey(Emulator.getGameEnvironment().getAchievementManager().getAchievement("Login"))) {
            AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("Login"));
        } else {

            long daysBetween = DAYS.between(new Date((long) this.client.getHabbo().getHabboInfo().getLastOnline() * 1000L).toInstant(), new Date().toInstant());


            Date lastLogin = new Date(this.client.getHabbo().getHabboInfo().getLastOnline());
            Calendar c1 = Calendar.getInstance();
            c1.add(Calendar.DAY_OF_YEAR, -1);

            Calendar c2 = Calendar.getInstance();
            c2.setTime(lastLogin);

            if (daysBetween == 1) {
                if (this.client.getHabbo().getHabboStats().getAchievementProgress().get(Emulator.getGameEnvironment().getAchievementManager().getAchievement("Login")) == this.client.getHabbo().getHabboStats().loginStreak) {
                    AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("Login"));
                }
                this.client.getHabbo().getHabboStats().loginStreak++;
            } else if (daysBetween >= 1) {
                // Calendar would be shown
            } else {
                if (((lastLogin.getTime() / 1000) - Emulator.getIntUnixTimestamp()) > 86400) {
                    this.client.getHabbo().getHabboStats().loginStreak = 0;
                }
            }
        }

        if (!this.client.getHabbo().getHabboStats().getAchievementProgress().containsKey(Emulator.getGameEnvironment().getAchievementManager().getAchievement("RegistrationDuration"))) {
            AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("RegistrationDuration"), 0);
        } else {
            int daysRegistered = ((Emulator.getIntUnixTimestamp() - this.client.getHabbo().getHabboInfo().getAccountCreated()) / 86400);

            int days = this.client.getHabbo().getHabboStats().getAchievementProgress(
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("RegistrationDuration")
            );

            if (daysRegistered - days > 0) {
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("RegistrationDuration"), daysRegistered - days);
            }
        }

        if (!this.client.getHabbo().getHabboStats().getAchievementProgress().containsKey(Emulator.getGameEnvironment().getAchievementManager().getAchievement("TraderPass"))) {
            AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("TraderPass"));
        }


        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement achievementQueueStatement = connection.prepareStatement("SELECT * FROM users_achievements_queue WHERE user_id = ?")) {
            achievementQueueStatement.setInt(1, this.client.getHabbo().getHabboInfo().getId());

            try (ResultSet achievementSet = achievementQueueStatement.executeQuery()) {
                while (achievementSet.next()) {
                    AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement(achievementSet.getInt("achievement_id")), achievementSet.getInt("amount"));
                }
            }

            try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM users_achievements_queue WHERE user_id = ?")) {
                deleteStatement.setInt(1, this.client.getHabbo().getHabboInfo().getId());
                deleteStatement.execute();
            }
        }
        if (Emulator.getConfig().getBoolean("hotel.calendar.enabled")) {
            CalendarCampaign campaign = Emulator.getGameEnvironment().getCalendarManager().getCalendarCampaign(Emulator.getConfig().getValue("hotel.calendar.default"));
            if(campaign != null){
                long daysBetween = DAYS.between(new Timestamp(campaign.getStartTimestamp() * 1000L).toInstant(), new Date().toInstant());
                if(daysBetween >= 0) {
                    this.client.sendResponse(new AdventCalendarDataComposer(campaign.getName(), campaign.getImage(), campaign.getTotalDays(), (int) daysBetween, this.client.getHabbo().getHabboStats().calendarRewardsClaimed, campaign.getLockExpired()));
                    this.client.sendResponse(new NuxAlertComposer("openView/calendar"));
                }
            };
        }

        if (TargetOffer.ACTIVE_TARGET_OFFER_ID > 0) {
            TargetOffer offer = Emulator.getGameEnvironment().getCatalogManager().getTargetOffer(TargetOffer.ACTIVE_TARGET_OFFER_ID);

            if (offer != null) {
                this.client.sendResponse(new TargetedOfferComposer(this.client.getHabbo(), offer));
            }
        }

        this.client.getHabbo().getHabboInfo().setLastOnline(Emulator.getIntUnixTimestamp());
    }
}
