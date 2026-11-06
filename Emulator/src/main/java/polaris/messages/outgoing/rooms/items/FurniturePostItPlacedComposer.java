package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FurniturePostItPlacedComposer extends MessageComposer {
    private final UserItem item;
    private final int unknownInt;

    public FurniturePostItPlacedComposer(UserItem item, int unknownInt) {
        this.item = item;
        this.unknownInt = unknownInt;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniturePostItPlacedComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        this.response.appendInt(this.unknownInt);
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }

    public int getUnknownInt() {
        return unknownInt;
    }
}
