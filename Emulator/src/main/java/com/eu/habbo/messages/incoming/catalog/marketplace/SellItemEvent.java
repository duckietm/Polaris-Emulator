package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceItemPostedComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellItemEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SellItemEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(new MarketplaceItemPostedComposer(MarketplaceItemPostedComposer.MARKETPLACE_DISABLED));
            return;
        }

        final int credits = this.packet.readInt();
        final int furniType = this.packet.readInt(); // 1 = FLOOR_TYPE, 2 = WALL_TYPE
        final int itemId = this.packet.readInt();

        if (!MarketplaceInputGuard.isPositiveId(itemId)) return;
        if (furniType != 1 && furniType != 2) return;

        HabboItem item = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);
        if (item != null) {
            if (!item.getBaseItem().allowMarketplace()) {
                String message = Emulator.getTexts().getValue("scripter.warning.marketplace.forbidden").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()).replace("%itemname%", item.getBaseItem().getName()).replace("%credits%", credits + "");
                ScripterManager.scripterDetected(this.client, message);
                LOGGER.info(message);
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            if (!MarketPlace.isValidListingPrice(credits)) {
                String message = Emulator.getTexts().getValue("scripter.warning.marketplace.negative").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()).replace("%itemname%", item.getBaseItem().getName()).replace("%credits%", credits + "");
                ScripterManager.scripterDetected(this.client, message);
                LOGGER.info(message);
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            if (MarketPlace.sellItem(this.client, item, credits)) {
                this.client.sendResponse(new MarketplaceItemPostedComposer(MarketplaceItemPostedComposer.POST_SUCCESS));
            } else {
                this.client.sendResponse(new MarketplaceItemPostedComposer(MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR));
            }
        }
    }
}
