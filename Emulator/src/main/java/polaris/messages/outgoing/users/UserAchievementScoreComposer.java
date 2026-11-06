package polaris.messages.outgoing.users;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserAchievementScoreComposer extends MessageComposer {
    private final User user;

    public UserAchievementScoreComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserAchievementScoreComposer);
        this.response.appendInt(this.user.getUserStats().getAchievementScore());
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
