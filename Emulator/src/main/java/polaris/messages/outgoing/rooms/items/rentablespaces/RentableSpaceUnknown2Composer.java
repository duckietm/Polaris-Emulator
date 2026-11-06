package polaris.messages.outgoing.rooms.items.rentablespaces;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * @deprecated kept for the plugin ABI; the value is what {@link RentableSpaceRentFailedComposer} sends
 *     (the old class wrote the item id where the official client reads the expiry time or the reason).
 */
@Deprecated
public class RentableSpaceUnknown2Composer extends MessageComposer {
    private final int itemId;

    public RentableSpaceUnknown2Composer(int itemId) {
        this.itemId = itemId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RentableSpaceUnknown2Composer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.itemId));
        return this.response;
    }

    public int getItemId() {
        return itemId;
    }
}
