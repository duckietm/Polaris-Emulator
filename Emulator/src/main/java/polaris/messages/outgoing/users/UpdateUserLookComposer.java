package polaris.messages.outgoing.users;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UpdateUserLookComposer extends MessageComposer {
    private final User user;

    public UpdateUserLookComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UpdateUserLookComposer);
        this.response.appendString(this.user.getUserInfo().getLook());
        this.response.appendString(this.user.getUserInfo().getGender().name());
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
