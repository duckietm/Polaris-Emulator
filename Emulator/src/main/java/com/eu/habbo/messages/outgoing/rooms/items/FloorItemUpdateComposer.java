package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.FurnitureUsagePolicy;
import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.items.interactions.InteractionMusicDisc;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.items.interactions.NikoTrophy;
import com.eu.habbo.habbohotel.items.interactions.StackHelperExtradata;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class FloorItemUpdateComposer extends MessageComposer {
    private final HabboItem item;

    public FloorItemUpdateComposer(HabboItem item) {
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

    public HabboItem getItem() {
        return item;
    }
}
