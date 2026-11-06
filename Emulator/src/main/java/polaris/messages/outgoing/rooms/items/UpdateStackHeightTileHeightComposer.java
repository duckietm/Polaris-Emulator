package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UpdateStackHeightTileHeightComposer extends MessageComposer {
    private final UserItem item;
    private final int height;

    public UpdateStackHeightTileHeightComposer(UserItem item, int height) {
        this.item = item;
        this.height = height;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UpdateStackHeightTileHeightComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        this.response.appendInt(this.height);
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }

    public int getHeight() {
        return height;
    }
}
