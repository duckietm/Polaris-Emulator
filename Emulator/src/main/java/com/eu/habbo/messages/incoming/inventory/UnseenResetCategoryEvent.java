package com.eu.habbo.messages.incoming.inventory;

import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Official {@code UnseenResetCategoryMessageComposer} (3493): the user opened an inventory tab,
 * so everything the tracker still counted as new in that category has now been seen
 * ({@code FurniModel/BadgesModel/BotsModel/PetsModel.resetUnseenItems}).
 */
public class UnseenResetCategoryEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int category = this.packet.readInt();

        if (category <= 0) {
            return;
        }

        this.client.getHabbo().getInventory().getUnseenItemsComponent().resetCategory(category);
    }
}
