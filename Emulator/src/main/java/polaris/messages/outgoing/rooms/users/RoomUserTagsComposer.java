package polaris.messages.outgoing.rooms.users;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserTagsComposer extends MessageComposer {
    private final User user;

    public RoomUserTagsComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserTagsComposer);
        this.response.appendInt(this.user.getRoomUnit().getId());
        this.response.appendInt(this.user.getUserStats().tags.length);

        for (String tag : this.user.getUserStats().tags) {
            this.response.appendString(tag);
        }
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
