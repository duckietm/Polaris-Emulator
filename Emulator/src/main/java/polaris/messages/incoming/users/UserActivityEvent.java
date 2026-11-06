package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.messages.incoming.MessageHandler;

public class UserActivityEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        String type = this.packet.readString();
        String value = this.packet.readString();
        String action = this.packet.readString();

        if (type.equals("Quiz")) {
            if (value.equalsIgnoreCase("7")) {
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("SafetyQuizGraduate"));
            }
        }

        switch (action) {
            case "forum.can.read.seen":
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment()
                                .getAchievementManager()
                                .getAchievement("SelfModForumCanReadSeen"));
                break;
            case "forum.can.post.seen":
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment()
                                .getAchievementManager()
                                .getAchievement("SelfModForumCanPostSeen"));
                break;
            case "forum.can.start.thread.seen":
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment()
                                .getAchievementManager()
                                .getAchievement("SelfModForumCanPostThrdSeen"));
                break;
            case "forum.can.moderate.seen":
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment()
                                .getAchievementManager()
                                .getAchievement("SelfModForumCanModerateSeen"));
                break;
            case "room.settings.doormode.seen":
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModDoorModeSeen"));
                break;
            case "room.settings.walkthrough.seen":
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModWalkthroughSeen"));
                break;
            case "room.settings.chat.scrollspeed.seen":
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment()
                                .getAchievementManager()
                                .getAchievement("SelfModChatScrollSpeedSeen"));
                break;
            case "room.settings.chat.hearrange.seen":
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment()
                                .getAchievementManager()
                                .getAchievement("SelfModChatHearRangeSeen"));
                break;
            case "room.settings.chat.floodfilter.seen":
                AchievementManager.progressAchievement(
                        this.client.getUser(),
                        Emulator.getGameEnvironment()
                                .getAchievementManager()
                                .getAchievement("SelfModChatFloodFilterSeen"));
                break;
        }
    }
}
