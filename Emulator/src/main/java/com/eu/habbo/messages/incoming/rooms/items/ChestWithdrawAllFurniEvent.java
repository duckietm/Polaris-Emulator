package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniWithdrawHelper;
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
        if (!(item instanceof InteractionWiredChest chest)) return;

        if (!room.hasRights(habbo)) return;

        // A locked chest refuses hand withdrawals; the pushed state lets the window show the lock.
        if (chest.getContents().isLocked()) {
            this.client.sendResponse(new ChestDataComposer(chest));
            return;
        }

        List<ChestFurniStoredItem> removedItems = chest.getContents().removeAllFurniItems();

        ChestFurniWithdrawHelper.completeWithdraw(this.client, chest, removedItems);
    }
}
