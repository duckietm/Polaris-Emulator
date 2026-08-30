package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestDepositSession;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Start furni deposit mode (official Kigike / header 3514 wire shape).
 * We use header {@code ChestStartDepositEvent} (9324) because 3514 is unavailable in Nitro.
 *
 * <p>The button on the chest window sends nothing but the chest id; opening the window where items
 * are chosen is the server's job. It answers by opening a table -- the same one a wired contract
 * negotiation uses -- pointed at this chest, so the player picks furniture out of their inventory,
 * sees what they are about to hand over, and confirms.
 */
public class ChestStartDepositEvent extends MessageHandler {
    /** Long enough to go looking through a full inventory, short enough that a table is not left held. */
    private static final int DEPOSIT_TIMEOUT_SECONDS = 300;

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int chestItemId = this.packet.readInt();
        HabboItem item = room.getHabboItem(chestItemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        // Only furni chests take furniture; a coin chest is filled from the currency panel.
        if (!(chest instanceof InteractionWiredChestFurni)) return;
        if (!chest.getContents().isAccessDonate() && !room.hasRights(habbo)) return;

        // A locked chest is closed to the room in both directions, but never to its owner.
        if (chest.isLockedFor(habbo)) return;

        // Marks which chest the player is filling. The table below is how they choose what goes in,
        // but the one-item-at-a-time path reads this session too, so both stay usable.
        ChestDepositSession.start(habbo.getHabboInfo().getId(), chestItemId);

        room.getWiredRuntime().getTradingManager().openDeposit(habbo, chest, DEPOSIT_TIMEOUT_SECONDS);
    }
}
