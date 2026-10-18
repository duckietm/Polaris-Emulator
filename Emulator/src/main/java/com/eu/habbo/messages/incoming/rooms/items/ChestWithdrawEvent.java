package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionLog;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestWithdrawals;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Withdraw coins from a coin chest (AIR {@code CoinChestSubController}): {@code int chestId, int
 * amount}. The coins reach the wallet as credits. Allowed to the owner and to players with rights
 * while the lock does not stand in their way; a refusal answers fail 12 (no wired chest) or 15
 * (locked or no rights).
 */
public class ChestWithdrawEvent extends MessageHandler {
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

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int amount = this.packet.readInt();
        if (amount <= 0) return;

        HabboItem item = room.getHabboItem(itemId);
        int refusal = ChestTransactionFailures.withdrawRefusal(item, habbo, room.hasRights(habbo));
        if (!(item instanceof InteractionWiredChest chest) || refusal != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(habbo, refusal);
            return;
        }
        if (chest.chestKind() != ChestStorage.KIND_CURRENCY) return;

        ChestWithdrawals.Result result =
                ChestWithdrawals.coins(habbo, chest, room, amount, ChestTransactionLog.SOURCE_USER);
        if (!result.isEmpty()) ChestWithdrawals.confirm(habbo);
    }
}
