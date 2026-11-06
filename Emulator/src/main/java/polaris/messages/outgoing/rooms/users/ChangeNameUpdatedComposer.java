package polaris.messages.outgoing.rooms.users;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ChangeNameUpdatedComposer extends MessageComposer {
    private final User user;

    public ChangeNameUpdatedComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChangeNameUpdateComposer);
        this.response.appendInt(0);
        this.response.appendString(this.user.getUserInfo().getUsername());
        this.response.appendInt(0);
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
