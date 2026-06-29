package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

/**
 * Player deposits currency into a wired chest (Scrigno). Reads {@code int itemId, int currencyType,
 * int amount}; allowed if the chest permits donations or the user has room rights. Debits the user
 * (if affordable), adds to the pool (capped at the chest's capacity), logs it, pushes back the state.
 */
public class ChestDepositEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();
        int currencyType = this.packet.readInt();
        int amount = this.packet.readInt();
        if (amount <= 0) return;

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        ChestStorage contents = chest.getContents();

        if (!contents.isAccessDonate() && !room.hasRights(habbo)) return;

        int balance = (currencyType < 0)
                ? habbo.getHabboInfo().getCredits()
                : habbo.getHabboInfo().getCurrencyAmount(currencyType);
        if (balance < amount) return;

        int capacityLeft = contents.getCapacityMax() - contents.total(ChestStorage.KIND_CURRENCY);
        if (capacityLeft <= 0) return;
        if (amount > capacityLeft) amount = capacityLeft;

        if (currencyType < 0) habbo.giveCredits(-amount);
        else habbo.givePoints(currencyType, -amount);

        contents.add(ChestStorage.KIND_CURRENCY, currencyType, amount);
        contents.addLog(new ChestStorage.LogEntry("deposit", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), 0, amount));
        chest.persistContents();

        this.client.sendResponse(new ChestDataComposer(chest));
    }
}
