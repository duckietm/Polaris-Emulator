package polaris.messages.outgoing.rooms;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

public class KnockKnockUnknownComposer extends MessageComposer {
    private final User user;

    public KnockKnockUnknownComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(478); // TODO Hardcoded header
        this.response.appendString(this.user.getUserInfo().getUsername());
        this.response.appendInt(this.user.getUserInfo().getId());
        return this.response;
    }
}
