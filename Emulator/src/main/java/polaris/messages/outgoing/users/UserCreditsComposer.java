package polaris.messages.outgoing.users;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserCreditsComposer extends MessageComposer {
    private final User user;

    public UserCreditsComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserCreditsComposer);
        this.response.appendString(this.user.getUserInfo().getCredits() + ".0");
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
