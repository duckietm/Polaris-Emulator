package polaris.messages.outgoing.inventory;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RemoveUserItemComposer extends MessageComposer {
    private final int itemId;

    public RemoveUserItemComposer(final int itemId) {
        this.itemId = itemId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RemoveUserItemComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.itemId));
        return this.response;
    }

    public int getItemId() {
        return itemId;
    }
}
