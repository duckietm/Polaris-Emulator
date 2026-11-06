package polaris.messages.outgoing.rooms.users;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserRespectComposer extends MessageComposer {
    private final User user;

    public RoomUserRespectComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserRespectComposer);
        this.response.appendInt(this.user.getUserInfo().getId());
        this.response.appendInt(this.user.getUserStats().respectPointsReceived);
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
