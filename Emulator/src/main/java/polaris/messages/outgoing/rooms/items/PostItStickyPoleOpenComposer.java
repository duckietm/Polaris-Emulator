package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PostItStickyPoleOpenComposer extends MessageComposer {
    private final UserItem item;

    public PostItStickyPoleOpenComposer(UserItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PostItStickyPoleOpenComposer);
        this.response.appendInt(this.item == null ? -1234 : BuildersClubItemIds.toWire(this.item.getId()));
        this.response.appendString("");
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }
}
