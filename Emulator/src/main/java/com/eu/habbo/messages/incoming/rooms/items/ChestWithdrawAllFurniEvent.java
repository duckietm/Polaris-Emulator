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
 * Withdraw everything (AIR {@code WiredChestWrapperView.onWithdrawAllConfirmed}): {@code int chestId}.
 * Empties a furni chest into the inventory or a coin chest into the wallet.
 */
public class ChestWithdrawAllFurniEvent extends MessageHandler {
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

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        HabboItem item = room.getHabboItem(itemId);
        // A refusal is answered with its reason, not silence: 12 when this is no wired chest, 15 when
        // the chest is locked to this player or they lack the rights to withdraw.
        int refusal = ChestTransactionFailures.withdrawRefusal(item, habbo, room.hasRights(habbo));
        if (!(item instanceof InteractionWiredChest chest) || refusal != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(habbo, refusal);
            return;
        }

        ChestWithdrawals.Result result = chest.chestKind() == ChestStorage.KIND_CURRENCY
                ? ChestWithdrawals.coins(habbo, chest, room, -1, ChestTransactionLog.SOURCE_USER)
                : ChestWithdrawals.furni(
                        habbo, chest, room, ChestStorage::removeAllFurniItems, ChestTransactionLog.SOURCE_USER);
        if (!result.isEmpty()) ChestWithdrawals.confirm(habbo);
    }
}
