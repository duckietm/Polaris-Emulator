package polaris.messages.outgoing.wired;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class WiredOpenComposer extends MessageComposer {
    private final UserItem item;

    public WiredOpenComposer(UserItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredOpenComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }
}
