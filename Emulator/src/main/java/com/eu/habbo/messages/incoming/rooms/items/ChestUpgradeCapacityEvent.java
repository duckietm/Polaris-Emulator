package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

/**
 * Buys extra chest capacity (room-rights only): {@code int itemId, int quantity}. Each step adds
 * {@link ChestStorage#CAPACITY_STEP} (+5000) and costs {@link #COST_CREDITS} credits +
 * {@link #COST_DIAMONDS} diamonds (points type {@link #DIAMOND_TYPE}). All-or-nothing.
 */
public class ChestUpgradeCapacityEvent extends MessageHandler {
    public static final int COST_CREDITS = 10;
    public static final int COST_DIAMONDS = 10;
    public static final int DIAMOND_TYPE = 5;
    private static final int MAX_QUANTITY = 10;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();
        int quantity = this.packet.readInt();
        if (quantity <= 0) return;
        quantity = Math.min(quantity, MAX_QUANTITY);

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;
        if (!room.hasRights(habbo)) return;

        ChestStorage c = chest.getContents();
        if (c.getCapacityMax() + (ChestStorage.CAPACITY_STEP * quantity) > ChestStorage.MAX_CAPACITY) return;

        int totalCredits = COST_CREDITS * quantity;
        int totalDiamonds = COST_DIAMONDS * quantity;

        if (habbo.getHabboInfo().getCredits() < totalCredits) return;
        if (habbo.getHabboInfo().getCurrencyAmount(DIAMOND_TYPE) < totalDiamonds) return;

        habbo.giveCredits(-totalCredits);
        habbo.givePoints(DIAMOND_TYPE, -totalDiamonds);

        c.setCapacityMax(c.getCapacityMax() + (ChestStorage.CAPACITY_STEP * quantity));
        chest.persistContents();

        this.client.sendResponse(new ChestDataComposer(chest));
    }
}
