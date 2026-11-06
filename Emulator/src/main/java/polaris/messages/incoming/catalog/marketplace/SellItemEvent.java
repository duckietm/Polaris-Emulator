package polaris.messages.incoming.catalog.marketplace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.catalog.marketplace.MarketPlace;
import polaris.hotel.catalog.marketplace.MarketplaceTokenService;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceItemPostedComposer;

public class SellItemEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SellItemEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public String getRatelimitGroup() {
        return "marketplace.mutation";
    }

    @Override
    public void handle() throws Exception {
        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(
                    new MarketplaceItemPostedComposer(MarketplaceItemPostedComposer.MARKETPLACE_DISABLED));
            return;
        }

        final int credits = this.packet.readInt();
        final int furniType = this.packet.readInt(); // 1 = FLOOR_TYPE, 2 = WALL_TYPE
        final int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!MarketplaceInputGuard.isPositiveId(itemId)) return;
        if (furniType != 1 && furniType != 2) return;

        UserItem item = this.client.getUser().getInventory().getItemsComponent().getUserItem(itemId);
        if (item != null) {
            if (!item.getBaseItem().allowMarketplace()) {
                String message = Emulator.getTexts()
                        .getValue("scripter.warning.marketplace.forbidden")
                        .replace(
                                "%username%",
                                this.client.getUser().getUserInfo().getUsername())
                        .replace("%itemname%", item.getBaseItem().getName())
                        .replace("%credits%", credits + "");
                ScripterManager.scripterDetected(this.client, message);
                LOGGER.info(message);
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            if (!MarketPlace.isValidListingPrice(credits)) {
                String message = Emulator.getTexts()
                        .getValue("scripter.warning.marketplace.negative")
                        .replace(
                                "%username%",
                                this.client.getUser().getUserInfo().getUsername())
                        .replace("%itemname%", item.getBaseItem().getName())
                        .replace("%credits%", credits + "");
                ScripterManager.scripterDetected(this.client, message);
                LOGGER.info(message);
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            // With selling tokens on, the offer costs one token (AIR buy_marketplace_tokens); it comes back
            // when the offer could not be posted.
            MarketplaceTokenService tokens = MarketplaceTokenService.installed();
            boolean paysToken = tokens != null && tokens.settings().enabled();
            int userId = this.client.getUser().getUserInfo().getId();
            if (paysToken && !tokens.spend(userId, 1)) {
                this.client.sendResponse(
                        new MarketplaceItemPostedComposer(MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR));
                return;
            }

            if (MarketPlace.sellItem(this.client, item, credits)) {
                this.client.sendResponse(new MarketplaceItemPostedComposer(MarketplaceItemPostedComposer.POST_SUCCESS));
            } else {
                if (paysToken) tokens.refund(userId, 1);
                this.client.sendResponse(
                        new MarketplaceItemPostedComposer(MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR));
            }
        }
    }
}
