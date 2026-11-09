package polaris.messages.incoming.users;

import java.util.Map;
import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class UserActivityEvent extends MessageHandler {
    /** The "seen" achievements the client reports, by the action it sends. */
    static final Map<String, String> SEEN_ACHIEVEMENTS = Map.of(
            "forum.can.read.seen", "SelfModForumCanReadSeen",
            "forum.can.post.seen", "SelfModForumCanPostSeen",
            "forum.can.start.thread.seen", "SelfModForumCanPostThrdSeen",
            "forum.can.moderate.seen", "SelfModForumCanModerateSeen",
            "room.settings.doormode.seen", "SelfModDoorModeSeen",
            "room.settings.walkthrough.seen", "SelfModWalkthroughSeen",
            "room.settings.chat.scrollspeed.seen", "SelfModChatScrollSpeedSeen",
            "room.settings.chat.hearrange.seen", "SelfModChatHearRangeSeen",
            "room.settings.chat.floodfilter.seen", "SelfModChatFloodFilterSeen");

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        String type = this.packet.readString();
        String value = this.packet.readString();
        String action = this.packet.readString();

        if (type.equals("Quiz") && value.equalsIgnoreCase("7")) {
            claimOnce(this.client.getUser(), "SafetyQuizGraduate");
        }

        String seen = SEEN_ACHIEVEMENTS.get(action);
        if (seen != null) {
            claimOnce(this.client.getUser(), seen);
        }
    }

    /**
     * The server cannot check these claims, so each one counts once: a hotel that gives one of these achievements
     * more levels cannot have them climbed by repeating the packet.
     */
    static void claimOnce(User user, String name) {
        Achievement achievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement(name);
        if (achievement == null || user == null) return;
        if (user.getUserStats().getAchievementProgress(achievement) > 0) return;

        AchievementManager.progressAchievement(user, achievement);
    }
}
