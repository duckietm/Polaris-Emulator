package com.eu.habbo.habbohotel.guides;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GuideTour {
    private final Habbo noob;
    private final String helpRequest;
    private final Set<GuideChatMessage> sendMessages = new HashSet<>();
    private final Set<Integer> declinedHelpers = new HashSet<>();
    private final Map<Integer, Long> lastMessageAt = new HashMap<>();

    /** The longest guide chat message, and the shortest wait between two messages of one user. */
    public static final int MAX_MESSAGE_LENGTH = 255;

    public static final long MIN_MESSAGE_INTERVAL_MS = 500;

    /** The most messages one tour keeps: they were collected without a bound. */
    public static final int MAX_STORED_MESSAGES = 500;

    public int checkSum = 0;
    private Habbo helper;
    private int startTime;
    private int endTime;
    private boolean ended;
    private GuideRecommendStatus wouldRecommend = GuideRecommendStatus.UNKNOWN;

    public GuideTour(Habbo noob, String helpRequest) {
        this.noob = noob;
        this.helpRequest = helpRequest;

        AchievementManager.progressAchievement(
                this.noob,
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("GuideAdvertisementReader"));
    }

    public void finish() {
        // TODO Insert recommendation.
        // TODO Query messages.
    }

    public Habbo getNoob() {
        return this.noob;
    }

    public String getHelpRequest() {
        return this.helpRequest;
    }

    public Habbo getHelper() {
        return this.helper;
    }

    public void setHelper(Habbo helper) {
        this.helper = helper;
    }

    public void addMessage(GuideChatMessage message) {
        synchronized (this.sendMessages) {
            if (this.sendMessages.size() < MAX_STORED_MESSAGES) this.sendMessages.add(message);
        }
    }

    int messageCount() {
        synchronized (this.sendMessages) {
            return this.sendMessages.size();
        }
    }

    /** Whether the user may send a message now; records it when so. */
    public boolean allowMessage(int userId, long nowMillis) {
        synchronized (this.lastMessageAt) {
            Long last = this.lastMessageAt.get(userId);

            if (last != null && nowMillis - last < MIN_MESSAGE_INTERVAL_MS) return false;

            this.lastMessageAt.put(userId, nowMillis);

            return true;
        }
    }

    public GuideRecommendStatus getWouldRecommend() {
        return this.wouldRecommend;
    }

    public void setWouldRecommend(GuideRecommendStatus wouldRecommend) {
        this.wouldRecommend = wouldRecommend;

        if (this.wouldRecommend == GuideRecommendStatus.YES) {
            AchievementManager.progressAchievement(
                    this.getHelper(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("GuideRecommendation"));
        }
    }

    public void addDeclinedHelper(int userId) {
        this.declinedHelpers.add(userId);
    }

    public boolean hasDeclined(int userId) {
        return this.declinedHelpers.contains(userId);
    }

    public void end() {
        this.ended = true;
        this.endTime = Emulator.getIntUnixTimestamp();

        AchievementManager.progressAchievement(
                this.helper,
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("GuideEnrollmentLifetime"));
        AchievementManager.progressAchievement(
                this.helper,
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("GuideRequestHandler"));
        AchievementManager.progressAchievement(
                this.noob, Emulator.getGameEnvironment().getAchievementManager().getAchievement("GuideRequester"));
    }

    public boolean isEnded() {
        return this.ended;
    }

    public int getStartTime() {
        return this.startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public int getEndTime() {
        return this.endTime;
    }
}
