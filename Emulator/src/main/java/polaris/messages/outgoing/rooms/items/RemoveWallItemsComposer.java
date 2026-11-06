package polaris.messages.outgoing.rooms.items;

import java.util.Collection;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR 13 {@code ItemRemoveMultiple} (official id 2204, parser
 * {@code class_2992}): the wall-item twin of {@link RemoveFloorItemsComposer}.
 */
public class RemoveWallItemsComposer extends MessageComposer {

    private final Collection<Integer> itemIds;
    private final int pickerId;
    private final boolean byInvisibleFurniManager;

    public RemoveWallItemsComposer(Collection<Integer> itemIds, int pickerId) {
        this(itemIds, pickerId, false);
    }

    /** @param byInvisibleFurniManager AIR's trailing flag, as in {@link RemoveFloorItemComposer} */
    public RemoveWallItemsComposer(Collection<Integer> itemIds, int pickerId, boolean byInvisibleFurniManager) {
        this.itemIds = itemIds;
        this.pickerId = pickerId;
        this.byInvisibleFurniManager = byInvisibleFurniManager;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemRemoveMultipleComposer);

        this.response.appendInt(this.itemIds.size());
        for (Integer itemId : this.itemIds) {
            this.response.appendInt(BuildersClubItemIds.toWire(itemId));
        }
        this.response.appendInt(this.pickerId);
        this.response.appendBoolean(this.byInvisibleFurniManager);

        return this.response;
    }

    public Collection<Integer> getItemIds() {
        return this.itemIds;
    }

    public int getPickerId() {
        return this.pickerId;
    }

    public boolean isByInvisibleFurniManager() {
        return this.byInvisibleFurniManager;
    }
}
