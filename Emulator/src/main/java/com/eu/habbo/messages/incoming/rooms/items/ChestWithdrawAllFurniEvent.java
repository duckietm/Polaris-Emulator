package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniWithdrawHelper;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import java.util.List;

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

        int itemId = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        // A refusal is answered with its reason, not silence: 12 when this is no wired chest, 15 when
        // the chest is locked to this player or they lack the rights to withdraw.
        int refusal = ChestTransactionFailures.withdrawRefusal(item, habbo, room.hasRights(habbo));
        if (!(item instanceof InteractionWiredChest chest)) {
            ChestTransactionFailures.send(habbo, refusal);
            return;
        }

        // A locked chest refuses everyone but its owner; the pushed state lets the window show it.
        if (refusal != ChestTransactionFailures.NONE) {
            if (chest.isLockedFor(habbo)) {
                this.client.sendResponse(new ChestDataComposer(chest, this.client.getHabbo()));
            }
            ChestTransactionFailures.send(habbo, refusal);
            return;
        }

        List<ChestFurniStoredItem> removedItems = chest.getContents().removeAllFurniItems();

        ChestFurniWithdrawHelper.completeWithdraw(this.client, chest, room, removedItems);
    }
}
