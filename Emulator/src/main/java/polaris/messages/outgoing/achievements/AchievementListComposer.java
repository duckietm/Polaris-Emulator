package polaris.messages.outgoing.achievements;

import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AchievementListComposer extends MessageComposer {

    private final User user;

    public AchievementListComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AchievementListComposer);

        var achievements = Emulator.getGameEnvironment().getAchievementManager().getAchievements().values().stream()
                .sorted(java.util.Comparator.comparingInt(achievement -> achievement.id))
                .toList();
        this.response.appendInt(achievements.size());
        for (Achievement achievement : achievements) {
            AchievementProgressComposer.appendAchievement(this.response, this.user, achievement);
        }
        this.response.appendString("");

        // Keep legacy repeated records intact; state metadata is a whole-packet extension.
        this.response.appendInt(achievements.size());
        for (Achievement achievement : achievements) {
            this.response.appendInt(achievement.id);
            this.response.appendShort(achievement.state);
        }

        return this.response;
    }

    public User getUser() {
        return user;
    }
}
