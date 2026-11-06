package polaris.messages.outgoing.inventory;

import java.util.Collection;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR 13 {@code FurniListRemoveMultiple} (official id 2813, parser
 * {@code class_3613}): {@code int count [ int stripId ]}. The client drops the
 * whole batch from the furni model in one pass and resets the unseen items
 * once.
 */
public class RemoveUserItemsComposer extends MessageComposer {

    private final Collection<Integer> itemIds;

    public RemoveUserItemsComposer(Collection<Integer> itemIds) {
        this.itemIds = itemIds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniListRemoveMultipleComposer);

        this.response.appendInt(this.itemIds.size());
        for (Integer itemId : this.itemIds) {
            this.response.appendInt(BuildersClubItemIds.toWire(itemId));
        }

        return this.response;
    }

    public Collection<Integer> getItemIds() {
        return this.itemIds;
    }
}
