package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.Collection;

public class RoomFloorItemsComposer extends MessageComposer {
    private final Int2ObjectMap<String> furniOwnerNames;
    private final Collection<? extends HabboItem> items;

    public RoomFloorItemsComposer(Int2ObjectMap<String> furniOwnerNames, Collection<? extends HabboItem> items) {
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

        for (HabboItem item : this.items) {
            item.serializeFloorData(this.response);
            this.response.appendInt(
                item instanceof InteractionGift
                    ? ((((InteractionGift) item).getColorId() * 1000) + ((InteractionGift) item).getRibbonId())
                    : (item instanceof InteractionMusicDisc
                        ? ((InteractionMusicDisc) item).getSongId()
                        : (item instanceof InteractionStackWalkHelper ? 2147483001 : 1))
            );
            item.serializeExtradata(this.response);
            this.response.appendInt(-1);
            this.response.appendInt(item instanceof InteractionTeleport || item instanceof InteractionSwitch || item instanceof InteractionSwitchRemoteControl || item instanceof InteractionVendingMachine || item instanceof InteractionInformationTerminal || item instanceof InteractionPostIt || item instanceof InteractionPuzzleBox ? 2 : item.isUsable() ? 1 : 0);
            this.response.appendInt(item.getUserId());
            this.response.appendInt(item.getBaseItem().allowStack() ? 1 : 0);
            this.response.appendInt(item.getBaseItem().allowSit() ? 1 : 0);
            this.response.appendInt(item.getBaseItem().allowLay() ? 1 : 0);
            this.response.appendInt(item.getBaseItem().allowWalk() ? 1 : 0);
            this.response.appendInt(item.getBaseItem().getWidth());
            this.response.appendInt(item.getBaseItem().getLength());
            this.response.appendInt(item.getTeleportTargetId());
        }
        return this.response;
    }

    public Int2ObjectMap<String> getFurniOwnerNames() {
        return furniOwnerNames;
    }

    public Collection<? extends HabboItem> getItems() {
        return items;
    }
}
