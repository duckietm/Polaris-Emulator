package polaris.messages.outgoing.rooms.items;

import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class WallItemUpdateComposer extends MessageComposer {
    private final UserItem item;

    public WallItemUpdateComposer(UserItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WallItemUpdateComposer);
        this.item.serializeWallData(this.response);
        this.response.appendString(this.item.getUserId() + "");
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }
}
