package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestFurni;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-facing wired chest (Scrigno) state push. Sent on open and after every deposit / withdraw /
 * settings change so the client {@code FurnitureChestView} can render the full window (name, description,
 * balance, capacity, access flags, appearance, notification prefs). Phase-1 currency-only contents.
 * Wire layout:
 * <pre>
 *   int itemId, string name, string description,
 *   int capacityMax, int used,
 *   bool accessOpen, bool accessDonate, int appearanceState,
 *   bool notifyFull, bool notifyDonation, bool notifyWithdraw, bool notifyEmpty, bool notifyWired, int notifyMode,
 *   int entryCount, [int currencyType, int amount]*,
 *   int chestKind (0 = currency, 1 = furni),
 *   int furniCount, [int baseItemId, int quantity]*
 * </pre>
 * For a furni chest, {@code used} is the total stored furni count and the currency entry list is empty;
 * for a currency chest the furni list is empty.
 */
public class ChestDataComposer extends MessageComposer {
    private final InteractionWiredChest chest;

    public ChestDataComposer(InteractionWiredChest chest) {
        this.chest = chest;
    }

    @Override
    protected ServerMessage composeInternal() {
        ChestStorage c = this.chest.getContents();
        int chestKind = (this.chest instanceof InteractionWiredChestFurni)
                ? ChestStorage.KIND_FURNI : ChestStorage.KIND_CURRENCY;

        this.response.init(Outgoing.ChestDataComposer);
        this.response.appendInt(this.chest.getId());
        this.response.appendString(c.getName());
        this.response.appendString(c.getDescription());
        this.response.appendInt(c.getCapacityMax());
        this.response.appendInt(c.total(chestKind));
        this.response.appendBoolean(c.isAccessOpen());
        this.response.appendBoolean(c.isAccessDonate());
        this.response.appendInt(c.getAppearanceState());
        this.response.appendBoolean(c.isNotifyFull());
        this.response.appendBoolean(c.isNotifyDonation());
        this.response.appendBoolean(c.isNotifyWithdraw());
        this.response.appendBoolean(c.isNotifyEmpty());
        this.response.appendBoolean(c.isNotifyWired());
        this.response.appendInt(c.getNotifyMode());

        List<ChestStorage.Entry> entries = new ArrayList<>();
        for (ChestStorage.Entry e : c.entries()) {
            if (e.kind == ChestStorage.KIND_CURRENCY && e.quantity > 0) entries.add(e);
        }

        this.response.appendInt(entries.size());
        for (ChestStorage.Entry e : entries) {
            this.response.appendInt(e.type);
            this.response.appendInt(e.quantity);
        }

        // chest kind + furni contents (base item id + quantity per stored type)
        this.response.appendInt(chestKind);

        List<ChestStorage.Entry> furni = new ArrayList<>();
        for (ChestStorage.Entry e : c.entries()) {
            if (e.kind == ChestStorage.KIND_FURNI && e.quantity > 0) furni.add(e);
        }

        this.response.appendInt(furni.size());
        for (ChestStorage.Entry e : furni) {
            this.response.appendInt(e.type);
            this.response.appendInt(e.quantity);
        }

        return this.response;
    }
}
