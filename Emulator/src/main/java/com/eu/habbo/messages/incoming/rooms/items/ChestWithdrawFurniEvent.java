package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniWithdrawHelper;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

import java.util.List;

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
        if (!(item instanceof InteractionWiredChest chest)) return;

        if (!room.hasRights(habbo)) return;

        int requested = (amount < 0) ? Integer.MAX_VALUE : Math.min(amount, MAX_WITHDRAW_AMOUNT);
        if (requested <= 0) return;

        List<ChestFurniStoredItem> removedItems =
                chest.getContents().removeFurniByWireType(wallItem, typeId, legacyPosterId, requested);

        ChestFurniWithdrawHelper.completeWithdraw(this.client, chest, removedItems);
    }
}
