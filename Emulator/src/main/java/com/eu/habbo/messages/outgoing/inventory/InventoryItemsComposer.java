package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.procedure.TIntObjectProcedure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class InventoryItemsComposer extends MessageComposer implements TIntObjectProcedure<HabboItem> {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryItemsComposer.class);

    private final int fragmentNumber;
    private final int totalFragments;
    private final TIntObjectMap<HabboItem> items;

    public InventoryItemsComposer(int fragmentNumber, int totalFragments, TIntObjectMap<HabboItem> items) {
        this.fragmentNumber = fragmentNumber;
        this.totalFragments = totalFragments;
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        try {
            this.response.init(Outgoing.InventoryItemsComposer);
            this.response.appendInt(this.totalFragments);
            this.response.appendInt(this.fragmentNumber - 1);
            this.response.appendInt(this.items.size());

            this.items.forEachEntry(this);
            return this.response;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    @Override
    public boolean execute(int a, HabboItem habboItem) {
        this.response.appendInt(habboItem.getGiftAdjustedId());
        this.response.appendString(habboItem.getBaseItem().getType().code);
        this.response.appendInt(habboItem.getId());
        this.response.appendInt(habboItem.getBaseItem().getSpriteId());

        if (habboItem.getBaseItem().getName().equals("floor") || habboItem.getBaseItem().getName().equals("landscape") || habboItem.getBaseItem().getName().equals("song_disk") || habboItem.getBaseItem().getName().equals("wallpaper") || habboItem.getBaseItem().getName().equals("poster")) {
            switch (habboItem.getBaseItem().getName()) {
                case "landscape" -> this.response.appendInt(4);
                case "floor" -> this.response.appendInt(3);
                case "wallpaper" -> this.response.appendInt(2);
                case "poster" -> this.response.appendInt(6);
                case "song_disk" -> this.response.appendInt(8);
            }
            this.addExtraDataToResponse(habboItem);
        } else {
            if (habboItem.getBaseItem().getName().equals("gnome_box"))
                this.response.appendInt(13);
            else
                this.response.appendInt(habboItem instanceof InteractionGift gift ? (((gift.getColorId() * 1000) + gift.getRibbonId())) : 1);

            habboItem.serializeExtradata(this.response);
        }
        this.response.appendBoolean(habboItem.getBaseItem().allowRecyle());
        this.response.appendBoolean(habboItem.getBaseItem().allowTrade());
        this.response.appendBoolean(!habboItem.isLimited() && habboItem.getBaseItem().allowInventoryStack());
        this.response.appendBoolean(habboItem.getBaseItem().allowMarketplace());
        this.response.appendInt(-1);
        this.response.appendBoolean(true);
        this.response.appendInt(-1);


        if (habboItem.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.response.appendString("");
            if(habboItem.getBaseItem().getName().equals("song_disk")) {
                List<String> extraDataAsList = Arrays.asList(habboItem.getExtradata().split("\n"));
                this.response.appendInt(Integer.valueOf(extraDataAsList.getLast()));
                return true;
            }
            this.response.appendInt(habboItem instanceof InteractionGift gift ? (((gift.getColorId() * 1000) + gift.getRibbonId())) : 1);
        }



        return true;
    }

    public void addExtraDataToResponse(HabboItem habboItem) {
        this.response.appendInt(0);
        this.response.appendString(habboItem.getExtradata());
    }

    public int getFragmentNumber() {
        return fragmentNumber;
    }

    public int getTotalFragments() {
        return totalFragments;
    }

    public TIntObjectMap<HabboItem> getItems() {
        return items;
    }
}