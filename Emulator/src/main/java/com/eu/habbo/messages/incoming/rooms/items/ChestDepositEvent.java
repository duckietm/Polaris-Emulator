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
        int currencyType = this.packet.readInt();
        int amount = this.packet.readInt();
        // Any negative type is credits; canonicalise to -1 so the stored key can't be split
        // across -1/-2/-3 (which would strand a deposit that later withdraws under a different key).
        if (currencyType < 0) currencyType = -1;
        if (amount <= 0) return;

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        ChestStorage contents = chest.getContents();

        if (!contents.isAccessDonate() && !room.hasRights(habbo)) return;

        int balance = (currencyType < 0)
                ? habbo.getHabboInfo().getCredits()
                : habbo.getHabboInfo().getCurrencyAmount(currencyType);
        if (balance <= 0) return;

        // Never debit more than the user owns; the chest decides atomically how much fits.
        int desired = Math.min(amount, balance);
        int accepted = contents.depositCurrency(currencyType, desired);
        if (accepted <= 0) return;

        if (currencyType < 0) habbo.giveCredits(-accepted);
        else habbo.givePoints(currencyType, -accepted);

        contents.addLog(new ChestStorage.LogEntry("deposit", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), 0, accepted));
        chest.persistContents();

        this.client.sendResponse(new ChestDataComposer(chest));
    }
}
