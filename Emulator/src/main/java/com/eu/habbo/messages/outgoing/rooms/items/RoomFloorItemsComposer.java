package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;

import java.util.Collection;
import java.util.NoSuchElementException;

public class RoomFloorItemsComposer extends MessageComposer {
    private final TIntObjectMap<String> furniOwnerNames;
    private final Collection<? extends HabboItem> items;

    public RoomFloorItemsComposer(TIntObjectMap<String> furniOwnerNames, Collection<? extends HabboItem> items) {
        this.furniOwnerNames = furniOwnerNames;
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomFloorItemsComposer);

        TIntObjectIterator<String> iterator = this.furniOwnerNames.iterator();

        this.response.appendInt(this.furniOwnerNames.size());
        for (int i = this.furniOwnerNames.size(); i-- > 0; ) {
            try {
                iterator.advance();
                this.response.appendInt(iterator.key());
                this.response.appendString(iterator.value());
            } catch (NoSuchElementException e) {
                break;
            }
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

    public TIntObjectMap<String> getFurniOwnerNames() {
        return furniOwnerNames;
    }

    public Collection<? extends HabboItem> getItems() {
        return items;
    }
}
