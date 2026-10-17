package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionLog;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestWithdrawals;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Withdraw furni of one type from a furni chest (AIR {@code FurniChestSubController}):
 * {@code int chestId, ChestItemType (bool wall, int typeId, string posterId), int amount}. The items
 * themselves leave the chest for the inventory.
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
        // A refusal is answered with its reason, not silence: 12 when this is no wired chest, 15 when
        // the chest is locked to this player or they lack the rights to withdraw.
        int refusal = ChestTransactionFailures.withdrawRefusal(item, habbo, room.hasRights(habbo));
        if (!(item instanceof InteractionWiredChest chest) || refusal != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(habbo, refusal);
            return;
        }
        if (chest.chestKind() != ChestStorage.KIND_FURNI) return;

        int requested = (amount < 0) ? MAX_WITHDRAW_AMOUNT : Math.min(amount, MAX_WITHDRAW_AMOUNT);
        if (requested <= 0) return;

        ChestWithdrawals.Result result = ChestWithdrawals.furni(
                habbo,
                chest,
                room,
                storage -> storage.removeFurniByWireType(wallItem, typeId, legacyPosterId, requested),
                ChestTransactionLog.SOURCE_USER);
        if (!result.isEmpty()) ChestWithdrawals.confirm(habbo);
    }
}
