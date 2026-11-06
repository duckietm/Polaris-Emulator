package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR {@code ObjectRemove}: {@code string id, bool isExpired, int pickerId, int delay, bool byInvisibleFurniManager}.
 * The trailing flag tells the client that the furni only left because the invisible furni manager (an area hider)
 * hid it, so it keeps the furni's buffered labels, fx and highlights; every other removal sends false.
 */
public class RemoveFloorItemComposer extends MessageComposer {
    private final UserItem item;
    private final boolean noUser;
    private final boolean byInvisibleFurniManager;

    public RemoveFloorItemComposer(UserItem item) {
        this(item, false);
    }

    public RemoveFloorItemComposer(UserItem item, boolean noUser) {
        this(item, noUser, false);
    }

    public RemoveFloorItemComposer(UserItem item, boolean noUser, boolean byInvisibleFurniManager) {
        this.item = item;
        this.noUser = noUser;
        this.byInvisibleFurniManager = byInvisibleFurniManager;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RemoveFloorItemComposer);

        this.response.appendString(BuildersClubItemIds.toWire(this.item.getId()) + "");
        this.response.appendBoolean(false);
        this.response.appendInt(this.noUser ? 0 : this.item.getUserId());
        this.response.appendInt(0);
        this.response.appendBoolean(this.byInvisibleFurniManager);

        return this.response;
    }

    public UserItem getItem() {
        return item;
    }

    public boolean isNoUser() {
        return noUser;
    }

    public boolean isByInvisibleFurniManager() {
        return byInvisibleFurniManager;
    }
}
