package com.eu.habbo.messages.incoming.handshake;

import static java.time.temporal.ChronoUnit.DAYS;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarCampaign;
import com.eu.habbo.habbohotel.catalog.TargetOffer;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.TargetedOfferComposer;
import com.eu.habbo.messages.outgoing.events.calendar.AdventCalendarDataComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NuxAlertComposer;
import java.sql.Timestamp;
import java.util.Date;

public class UsernameEvent extends MessageHandler {
    public static void completeLogin(GameClient client) throws Exception {
        UsernameEvent event = new UsernameEvent();
        event.client = client;
        event.handle();
    }

    @Override
    public void handle() throws Exception {
        var habbo = this.client.getHabbo();
        var stats = habbo.getHabboStats();
        var achievements = Emulator.getGameEnvironment().getAchievementManager();
        int timestamp = Emulator.getIntUnixTimestamp();
        stats.loginStreak = loginStreak(stats.loginStreak, habbo.getHabboInfo().getLastOnline(), timestamp);

        var loginAchievement = achievements.getAchievement("Login");
        int loginProgress = Math.max(0, stats.getAchievementProgress(loginAchievement));
        if (stats.loginStreak > loginProgress) {
            AchievementManager.progressAchievement(habbo, loginAchievement, stats.loginStreak - loginProgress);
        }

        var registrationAchievement = achievements.getAchievement("RegistrationDuration");
        int daysRegistered = Math.max(0, (timestamp - habbo.getHabboInfo().getAccountCreated()) / 86400);
        int registrationProgress = Math.max(0, stats.getAchievementProgress(registrationAchievement));
        if (daysRegistered > registrationProgress) {
            AchievementManager.progressAchievement(
                    habbo, registrationAchievement, daysRegistered - registrationProgress);
        }

        var tradingAchievement = achievements.getAchievement("TraderPass");
        if (stats.getAchievementProgress(tradingAchievement) < 0)
            AchievementManager.progressAchievement(habbo, tradingAchievement);
        AchievementManager.processQueuedAchievements(habbo);
        var onlineTimeAchievement = achievements.getAchievement("AllTimeHotelPresence");
        int onlineProgress = Math.max(0, stats.getAchievementProgress(onlineTimeAchievement));
        int onlineMinutes = stats.getOnlineMinutes(timestamp);
        if (onlineMinutes > onlineProgress)
            AchievementManager.progressAchievement(habbo, onlineTimeAchievement, onlineMinutes - onlineProgress);

        if (AchievementManager.TALENTTRACK_ENABLED) {
            for (var type : com.eu.habbo.habbohotel.achievements.TalentTrackType.values()) {
                if (achievements.getTalenTrackLevels(type) != null)
                    achievements.handleTalentTrackAchievement(habbo, type, null);
            }
        }

        if (Emulator.getConfig().getBoolean("hotel.calendar.enabled")) {
            CalendarCampaign campaign = Emulator.getGameEnvironment()
                    .getCalendarManager()
                    .getCalendarCampaign(Emulator.getConfig().getValue("hotel.calendar.default"));
            if (campaign != null) {
                long daysBetween = DAYS.between(
                        new Timestamp(campaign.getStartTimestamp() * 1000L).toInstant(), new Date().toInstant());
                if (daysBetween >= 0) {
                    this.client.sendResponse(new AdventCalendarDataComposer(
                            campaign.getName(),
                            campaign.getImage(),
                            campaign.getTotalDays(),
                            (int) daysBetween,
                            this.client.getHabbo().getHabboStats().calendarRewardsClaimed,
                            campaign.getLockExpired()));
                    this.client.sendResponse(new NuxAlertComposer("openView/calendar"));
                }
            }
            ;
        }

        if (TargetOffer.ACTIVE_TARGET_OFFER_ID > 0) {
            TargetOffer offer = Emulator.getGameEnvironment()
                    .getCatalogManager()
                    .getTargetOffer(TargetOffer.ACTIVE_TARGET_OFFER_ID);

            if (offer != null) {
                this.client.sendResponse(new TargetedOfferComposer(this.client.getHabbo(), offer));
            }
        }

        this.client.getHabbo().getHabboInfo().setLastOnline(Emulator.getIntUnixTimestamp());
    }

    static int loginStreak(int previousStreak, int previousLogin, int timestamp) {
        int elapsedDays = Math.floorDiv(timestamp, 86400) - Math.floorDiv(previousLogin, 86400);
        if (elapsedDays <= 0) return Math.max(1, previousStreak);
        return elapsedDays == 1 ? Math.max(1, previousStreak) + 1 : 1;
    }
}
