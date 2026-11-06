package polaris.messages.outgoing.friends;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FriendRequestComposer extends MessageComposer {
    private final User user;

    public FriendRequestComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FriendRequestComposer);

        this.response.appendInt(this.user.getUserInfo().getId());
        this.response.appendString(this.user.getUserInfo().getUsername());
        this.response.appendString(this.user.getUserInfo().getLook());

        return this.response;
    }

    public User getUser() {
        return user;
    }
}
