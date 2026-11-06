package polaris.messages.incoming.handshake;

import static java.time.temporal.ChronoUnit.DAYS;

import java.sql.Timestamp;
import java.util.Date;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.campaign.calendar.CalendarCampaign;
import polaris.hotel.catalog.TargetOffer;
import polaris.hotel.gameclients.GameClient;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.TargetedOfferComposer;
import polaris.messages.outgoing.events.calendar.AdventCalendarDataComposer;
import polaris.messages.outgoing.hotelway.nux.NuxAlertComposer;

public class UsernameEvent extends MessageHandler {
    public static void completeLogin(GameClient client) throws Exception {
        UsernameEvent event = new UsernameEvent();
        event.client = client;
        event.handle();
    }

    @Override
    public void handle() throws Exception {
        var user = this.client.getUser();
        var stats = user.getUserStats();
        var achievements = Emulator.getGameEnvironment().getAchievementManager();
        int timestamp = Emulator.getIntUnixTimestamp();
        stats.loginStreak = loginStreak(stats.loginStreak, user.getUserInfo().getLastOnline(), timestamp);

        var loginAchievement = achievements.getAchievement("Login");
        int loginProgress = Math.max(0, stats.getAchievementProgress(loginAchievement));
        if (stats.loginStreak > loginProgress) {
            AchievementManager.progressAchievement(user, loginAchievement, stats.loginStreak - loginProgress);
        }

        var registrationAchievement = achievements.getAchievement("RegistrationDuration");
        int daysRegistered = Math.max(0, (timestamp - user.getUserInfo().getAccountCreated()) / 86400);
        int registrationProgress = Math.max(0, stats.getAchievementProgress(registrationAchievement));
        if (daysRegistered > registrationProgress) {
            AchievementManager.progressAchievement(
                    user, registrationAchievement, daysRegistered - registrationProgress);
        }

        var tradingAchievement = achievements.getAchievement("TraderPass");
        if (stats.getAchievementProgress(tradingAchievement) < 0)
            AchievementManager.progressAchievement(user, tradingAchievement);
        AchievementManager.processQueuedAchievements(user);
        var onlineTimeAchievement = achievements.getAchievement("AllTimeHotelPresence");
        int onlineProgress = Math.max(0, stats.getAchievementProgress(onlineTimeAchievement));
        int onlineMinutes = stats.getOnlineMinutes(timestamp);
        if (onlineMinutes > onlineProgress)
            AchievementManager.progressAchievement(user, onlineTimeAchievement, onlineMinutes - onlineProgress);

        if (AchievementManager.TALENTTRACK_ENABLED) {
            for (var type : polaris.hotel.achievements.TalentTrackType.values()) {
                if (achievements.getTalenTrackLevels(type) != null)
                    achievements.handleTalentTrackAchievement(user, type, null);
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
                            this.client.getUser().getUserStats().calendarRewardsClaimed,
                            campaign.getLockExpired(),
                            campaign.getRewards(),
                            this.client.getUser()));
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
                this.client.sendResponse(new TargetedOfferComposer(this.client.getUser(), offer));
            }
        }

        this.client.getUser().getUserInfo().setLastOnline(Emulator.getIntUnixTimestamp());
    }

    /**
     * IsFirstLoginOfDay (793): the quest engine opens the seasonal calendar on the first login of a
     * day. The previous login is still the stored {@code last_online} here, it is moved at the end of
     * {@link #handle()}; days are counted the way {@link #loginStreak} counts them.
     */
    public static boolean isFirstLoginOfDay(int previousLogin, int timestamp) {
        return previousLogin <= 0 || Math.floorDiv(timestamp, 86400) > Math.floorDiv(previousLogin, 86400);
    }

    static int loginStreak(int previousStreak, int previousLogin, int timestamp) {
        int elapsedDays = Math.floorDiv(timestamp, 86400) - Math.floorDiv(previousLogin, 86400);
        if (elapsedDays <= 0) return Math.max(1, previousStreak);
        return elapsedDays == 1 ? Math.max(1, previousStreak) + 1 : 1;
    }
}
