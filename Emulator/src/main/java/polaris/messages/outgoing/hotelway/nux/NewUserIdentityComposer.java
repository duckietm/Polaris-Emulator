package polaris.messages.outgoing.hotelway.nux;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NewUserIdentityComposer extends MessageComposer {
    private final User user;

    public NewUserIdentityComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewUserIdentityComposer);
        this.response.appendInt(this.user.noobStatus());
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
