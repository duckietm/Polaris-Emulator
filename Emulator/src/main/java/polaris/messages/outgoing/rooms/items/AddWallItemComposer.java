package polaris.messages.outgoing.rooms.items;

import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AddWallItemComposer extends MessageComposer {
    private final UserItem item;
    private final String itemOwnerName;

    public AddWallItemComposer(UserItem item, String itemOwnerName) {
        this.item = item;
        this.itemOwnerName = itemOwnerName;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AddWallItemComposer);
        this.item.serializeWallData(this.response);
        this.response.appendString(this.itemOwnerName);
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }

    public String getItemOwnerName() {
        return itemOwnerName;
    }
}
