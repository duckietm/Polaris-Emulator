package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

/**
 * Player withdraws stored furni from a wired furni chest (Scrigno furni). Reads {@code int itemId,
 * int baseItemId, int amount} (amount {@code < 0} = withdraw all of that base type). Restricted to users
 * with room rights (anti-theft). Decrements the chest pool, creates that many items into the player's
 * inventory, logs it, and pushes back the chest state. Mirrors {@link ChestWithdrawEvent} (currency).
 */
public class ChestWithdrawFurniEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();
        int baseItemId = this.packet.readInt();
        int amount = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        if (!room.hasRights(habbo)) return;

        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(baseItemId);
        if (baseItem == null) return;

        ChestStorage contents = chest.getContents();
        int available = contents.count(ChestStorage.KIND_FURNI, baseItemId);
        if (available <= 0) return;

        int requested = (amount < 0) ? available : Math.min(amount, available);
        if (requested <= 0) return;

        int taken = contents.take(ChestStorage.KIND_FURNI, baseItemId, requested);
        if (taken <= 0) return;

        for (int i = 0; i < taken; i++) {
            HabboItem created = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");
            if (created == null) continue;
            this.client.sendResponse(new AddHabboItemComposer(created));
            habbo.getInventory().getItemsComponent().addItem(created);
        }
        this.client.sendResponse(new InventoryRefreshComposer());

        contents.addLog(new ChestStorage.LogEntry("withdraw", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), taken, 0));
        chest.persistContents();

        this.client.sendResponse(new ChestDataComposer(chest));
    }
}
