package polaris.messages.incoming.catalog.marketplace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import polaris.messages.outgoing.catalog.marketplace.MarketplaceOwnItemsComposer;

/**
 * AIR 13 composer 1551 (`MarketplaceModel.makeOffer`): one price, one furni type and a
 * list of item ids, so the seller lists several identical copies in a single action. The
 * official client builds the list out of the inventory selection and never mixes floor
 * and wall items, so every id has to be of the announced type.
 *
 * <p>Each copy becomes its own marketplace offer at the given price, exactly as if the
 * player had posted them one by one with the single-item composer.
 */
public class SellMultipleItemsEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SellMultipleItemsEvent.class);

    /** The official inventory selection is capped; refuse anything that cannot be a real selection. */
    public static final int MAXIMUM_ITEMS_PER_OFFER = 100;

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
        final int credits = this.packet.readInt();
        final int furniType = this.packet.readInt(); // 1 = FLOOR_TYPE, 2 = WALL_TYPE
        final int count = this.packet.readInt();

        if (count <= 0 || count > MAXIMUM_ITEMS_PER_OFFER) return;

        Set<Integer> itemIds = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            itemIds.add(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));
        }

        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(
                    new MarketplaceItemPostedComposer(MarketplaceItemPostedComposer.MARKETPLACE_DISABLED));
            return;
        }

        if (furniType != 1 && furniType != 2) return;

        List<UserItem> items = new ArrayList<>();
        for (int itemId : itemIds) {
            if (!MarketplaceInputGuard.isPositiveId(itemId)) return;

            UserItem item =
                    this.client.getUser().getInventory().getItemsComponent().getUserItem(itemId);
            if (item == null) continue;

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

            items.add(item);
        }

        if (items.isEmpty()) return;

        if (!MarketPlace.isValidListingPrice(credits)) {
            String message = Emulator.getTexts()
                    .getValue("scripter.warning.marketplace.negative")
                    .replace("%username%", this.client.getUser().getUserInfo().getUsername())
                    .replace("%itemname%", items.getFirst().getBaseItem().getName())
                    .replace("%credits%", credits + "");
            ScripterManager.scripterDetected(this.client, message);
            LOGGER.info(message);
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        // With selling tokens on, every copy is its own offer and costs one token. Posting stops at the last
        // token, and a copy that could not be posted gives its token back.
        MarketplaceTokenService tokens = MarketplaceTokenService.installed();
        boolean paysTokens = tokens != null && tokens.settings().enabled();
        int userId = this.client.getUser().getUserInfo().getId();

        int posted = 0;
        for (UserItem item : items) {
            if (paysTokens && !tokens.spend(userId, 1)) break;

            if (MarketPlace.sellItem(this.client, item, credits)) {
                posted++;
            } else if (paysTokens) {
                tokens.refund(userId, 1);
            }
        }

        this.client.sendResponse(new MarketplaceItemPostedComposer(
                posted > 0
                        ? MarketplaceItemPostedComposer.POST_SUCCESS
                        : MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR));

        if (posted > 0) {
            this.client.sendResponse(new MarketplaceOwnItemsComposer(this.client.getUser()));
        }
    }
}
