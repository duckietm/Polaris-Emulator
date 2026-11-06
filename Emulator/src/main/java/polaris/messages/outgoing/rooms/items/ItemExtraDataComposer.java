package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ItemExtraDataComposer extends MessageComposer {
    private final UserItem item;

    public ItemExtraDataComposer(UserItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemExtraDataComposer);
        this.response.appendString(BuildersClubItemIds.toWire(this.item.getId()) + "");
        this.item.serializeExtradata(this.response);
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }
}
