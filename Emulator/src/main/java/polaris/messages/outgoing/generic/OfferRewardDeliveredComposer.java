package polaris.messages.outgoing.generic;

import polaris.hotel.items.Item;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class OfferRewardDeliveredComposer extends MessageComposer {
    private final Item item;

    public OfferRewardDeliveredComposer(Item item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.OfferRewardDeliveredComposer);
        this.response.appendString(this.item.getType().code);
        this.response.appendInt(this.item.getId());
        this.response.appendString(this.item.getName());
        this.response.appendString(this.item.getDisplayName());
        return this.response;
    }

    public Item getItem() {
        return item;
    }
}
