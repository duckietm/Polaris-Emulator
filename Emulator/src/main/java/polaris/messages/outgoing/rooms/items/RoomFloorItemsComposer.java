package polaris.messages.outgoing.rooms.items;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Collection;
import polaris.hotel.items.FurnitureUsagePolicy;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.items.interactions.InteractionMusicDisc;
import polaris.hotel.items.interactions.InteractionStackWalkHelper;
import polaris.hotel.items.interactions.InteractionTileWalkMagic;
import polaris.hotel.items.interactions.NikoTrophy;
import polaris.hotel.items.interactions.StackHelperExtradata;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomFloorItemsComposer extends MessageComposer {
    private final Int2ObjectMap<String> furniOwnerNames;
    private final Collection<? extends UserItem> items;

    public RoomFloorItemsComposer(Int2ObjectMap<String> furniOwnerNames, Collection<? extends UserItem> items) {
        this.furniOwnerNames = furniOwnerNames;
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomFloorItemsComposer);

        this.response.appendInt(this.furniOwnerNames.size());
        for (Int2ObjectMap.Entry<String> entry : this.furniOwnerNames.int2ObjectEntrySet()) {
            this.response.appendInt(entry.getIntKey());
            this.response.appendString(entry.getValue());
        }

        this.response.appendInt(this.items.size());

        for (UserItem item : this.items) {
            item.serializeFloorData(this.response);
            this.response.appendInt(
                    item instanceof InteractionGift
                            ? ((((InteractionGift) item).getColorId() * 1000) + ((InteractionGift) item).getRibbonId())
                            : (item instanceof InteractionMusicDisc
                                    ? ((InteractionMusicDisc) item).getSongId()
                                    : (item instanceof InteractionStackWalkHelper
                                            ? 2147483001
                                            : (item instanceof InteractionTileWalkMagic
                                                    ? StackHelperExtradata.multiWalkExtra(item.getExtradata())
                                                    : NikoTrophy.extraOr(item, 1)))));
            item.serializeExtradata(this.response);
            this.response.appendInt(item.getSecondsToExpiration());
            this.response.appendInt(FurnitureUsagePolicy.of(item));
            this.response.appendInt(item.getUserId());
            this.response.appendInt(item.getBaseItem().allowStack() ? 1 : 0);
            this.response.appendInt(item.getBaseItem().allowSit() ? 1 : 0);
            this.response.appendInt(item.getBaseItem().allowLay() ? 1 : 0);
            this.response.appendInt(item.getBaseItem().allowWalk() ? 1 : 0);
            this.response.appendInt(item.getBaseItem().getWidth());
            this.response.appendInt(item.getBaseItem().getLength());
            this.response.appendInt(BuildersClubItemIds.toWire(item.getTeleportTargetId()));
        }
        return this.response;
    }

    public Int2ObjectMap<String> getFurniOwnerNames() {
        return furniOwnerNames;
    }

    public Collection<? extends UserItem> getItems() {
        return items;
    }
}
