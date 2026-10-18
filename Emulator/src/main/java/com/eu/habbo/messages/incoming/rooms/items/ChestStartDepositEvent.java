package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Start a deposit (AIR {@code WiredChestWrapperView.onDepositClick}): {@code int chestId}.
 *
 * <p>The only way into a chest. The server answers by opening the wired trade window pointed at the
 * chest: the player puts inventory items on the table and confirms. A coin chest takes credit furni,
 * which become its coins; a furni chest takes furni. Allowed to anyone when the chest takes donations,
 * else to the owner and players with rights while the lock does not stand in their way; a refusal
 * answers fail 12 or 15.
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

        int chestItemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        HabboItem item = room.getHabboItem(chestItemId);

        int refusal = ChestTransactionFailures.depositRefusal(item, habbo, room.hasRights(habbo));
        if (!(item instanceof InteractionWiredChest chest) || refusal != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(habbo, refusal);
            return;
        }

        room.getWiredRuntime().getTradingManager().openDeposit(habbo, chest, DEPOSIT_TIMEOUT_SECONDS);
    }
}
