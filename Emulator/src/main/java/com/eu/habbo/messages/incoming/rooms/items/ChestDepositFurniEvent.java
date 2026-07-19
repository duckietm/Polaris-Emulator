package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniPackets;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;

import java.util.ArrayList;
import java.util.List;

/**
 * Player deposits floor furni from inventory into a wired furni chest (Scrigno furni).
 * Reads {@code int itemId, int baseItemId, int amount}. Allowed if the chest permits donations
 * or the user has room rights. Removes matching inventory rows, adds to the chest pool (capped
 * at capacity), logs, and pushes back chest state + inventory updates.
 */
public class ChestDepositFurniEvent extends MessageHandler {
    private static final int MAX_DEPOSIT_AMOUNT = 100;

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
        int baseItemId = this.packet.readInt();
        int amount = this.packet.readInt();
        if (amount <= 0 || amount > MAX_DEPOSIT_AMOUNT) return;

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        ChestStorage contents = chest.getContents();

        if (!contents.isAccessDonate() && !room.hasRights(habbo)) return;

        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(baseItemId);
        if (baseItem == null || baseItem.getType() != FurnitureType.FLOOR) return;

        int availableInInventory = countInInventory(habbo, baseItemId);
        if (availableInInventory <= 0) return;

        int capacityLeft = contents.getCapacityMax() - contents.furniItemCount();
        if (capacityLeft <= 0) return;

        amount = Math.min(amount, Math.min(availableInInventory, capacityLeft));

        var toRemove = new ArrayList<HabboItem>();
        var added = new ArrayList<ChestFurniStoredItem>();
        for (int i = 0; i < amount; i++) {
            HabboItem removed = habbo.getInventory().getItemsComponent().getAndRemoveHabboItem(baseItem);
            if (removed == null) break;

            ChestFurniStoredItem stored = ChestFurniStoredItem.fromHabboItem(removed, 0);

            // Atomic capacity-guarded add: if a concurrent deposit filled the chest between the
            // capacity check above and here, put the item straight back so it can't be lost.
            if (!contents.tryDepositFurni(stored)) {
                habbo.getInventory().getItemsComponent().addItem(removed);
                break;
            }

            toRemove.add(removed);
            added.add(stored);
        }
        int deposited = toRemove.size();
        if (deposited <= 0) return;

        contents.addLog(new ChestStorage.LogEntry("deposit", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), 0, deposited));
        chest.persistContents();

        for (HabboItem removed : toRemove) {
            habbo.getClient().sendResponse(new RemoveHabboItemComposer(removed.getGiftAdjustedId()));
        }
        habbo.getClient().sendResponse(new InventoryRefreshComposer());
        Emulator.getThreading().runPersistence(new QueryDeleteHabboItems(toRemove));

        this.client.sendResponse(new ChestDataComposer(chest));
        ChestFurniPackets.sendDelta(this.client, chest.getId(), List.of(), added);
    }

    private static int countInInventory(Habbo habbo, int baseItemId) {
        int count = 0;
        for (HabboItem habboItem : habbo.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (habboItem != null && habboItem.getBaseItem().getId() == baseItemId) {
                count++;
            }
        }
        return count;
    }
}
