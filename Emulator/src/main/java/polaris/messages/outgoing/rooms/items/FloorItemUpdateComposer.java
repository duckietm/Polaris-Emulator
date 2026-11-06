package polaris.messages.outgoing.rooms.items;

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

public class FloorItemUpdateComposer extends MessageComposer {
    private final UserItem item;

    public FloorItemUpdateComposer(UserItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FloorItemUpdateComposer);
        this.item.serializeFloorData(this.response);
        this.response.appendInt(
                this.item instanceof InteractionGift
                        ? ((((InteractionGift) this.item).getColorId() * 1000)
                                + ((InteractionGift) this.item).getRibbonId())
                        : (this.item instanceof InteractionMusicDisc
                                ? ((InteractionMusicDisc) this.item).getSongId()
                                : (this.item instanceof InteractionStackWalkHelper
                                        ? 2147483001
                                        : (this.item instanceof InteractionTileWalkMagic
                                                ? StackHelperExtradata.multiWalkExtra(this.item.getExtradata())
                                                : NikoTrophy.extraOr(this.item, 0)))));
        this.item.serializeExtradata(this.response);
        this.response.appendInt(this.item.getSecondsToExpiration());
        this.response.appendInt(FurnitureUsagePolicy.of(this.item));
        this.response.appendInt(this.item.getUserId());
        this.response.appendInt(this.item.getBaseItem().allowStack() ? 1 : 0);
        this.response.appendInt(this.item.getBaseItem().allowSit() ? 1 : 0);
        this.response.appendInt(this.item.getBaseItem().allowLay() ? 1 : 0);
        this.response.appendInt(this.item.getBaseItem().allowWalk() ? 1 : 0);
        this.response.appendInt(this.item.getBaseItem().getWidth());
        this.response.appendInt(this.item.getBaseItem().getLength());
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getTeleportTargetId()));
        return this.response;
    }

    public UserItem getItem() {
        return item;
    }
}
