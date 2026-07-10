package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniPackets;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

import java.util.List;

/**
 * Player withdraws stored furni from a wired furni chest (official Dul wire shape):
 * {@code int chestItemId, bool isWall, int typeId, string legacyPosterId, int amount}.
 * amount {@code < 0} = withdraw all matching rows.
 */
public class ChestWithdrawFurniEvent extends MessageHandler {
    private static final int MAX_WITHDRAW_AMOUNT = 1000;

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();
        boolean wallItem = this.packet.readBoolean();
        int typeId = this.packet.readInt();
        String legacyPosterId = this.packet.readString();
        int amount = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        if (!room.hasRights(habbo)) return;

        // typeId is the CLIENT-facing type (sprite id) echoed back from what we sent on the wire.
        // amount < 0 = withdraw all matching rows; a positive amount is capped so a spoofed huge
        // value can't force an oversized allocation. removeFurniByWireType is atomic and caps at
        // whatever is actually present, so it can never over-withdraw or duplicate.
        ChestStorage contents = chest.getContents();
        int requested = (amount < 0) ? Integer.MAX_VALUE : Math.min(amount, MAX_WITHDRAW_AMOUNT);
        if (requested <= 0) return;

        var removedItems = contents.removeFurniByWireType(wallItem, typeId, legacyPosterId, requested);
        int taken = removedItems.size();
        if (taken <= 0) return;

        for (ChestFurniStoredItem stored : removedItems) {
            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(stored.baseItemId);
            if (baseItem == null) continue;

            HabboItem created = Emulator.getGameEnvironment().getItemManager().createItem(
                    habbo.getHabboInfo().getId(), baseItem, stored.limitedStack, stored.limitedSells, stored.extradata);
            if (created == null) continue;
            this.client.sendResponse(new AddHabboItemComposer(created));
            habbo.getInventory().getItemsComponent().addItem(created);
        }
        this.client.sendResponse(new InventoryRefreshComposer());

        contents.addLog(new ChestStorage.LogEntry("withdraw", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), taken, 0));
        chest.persistContents();

        this.client.sendResponse(new ChestDataComposer(chest));
        ChestFurniPackets.sendDelta(this.client, chest.getId(),
                removedItems.stream().map(i -> i.inventoryId).toList(), List.of());
    }
}
