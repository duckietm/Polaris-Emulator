package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR {@code ItemRemove}: {@code string id, int pickerId, bool byInvisibleFurniManager}; the flag as in {@link
 * RemoveFloorItemComposer}.
 */
public class RemoveWallItemComposer extends MessageComposer {
    private final UserItem item;
    private final boolean byInvisibleFurniManager;

    public RemoveWallItemComposer(UserItem item) {
        this(item, false);
    }

    public RemoveWallItemComposer(UserItem item, boolean byInvisibleFurniManager) {
        this.item = item;
        this.byInvisibleFurniManager = byInvisibleFurniManager;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RemoveWallItemComposer);
        this.response.appendString(BuildersClubItemIds.toWire(this.item.getId()) + "");
        this.response.appendInt(this.item.getUserId());
        this.response.appendBoolean(this.byInvisibleFurniManager);
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }

    public boolean isByInvisibleFurniManager() {
        return byInvisibleFurniManager;
    }
}
