package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceItemPostedComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceOwnItemsComposer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            itemIds.add(this.packet.readInt());
        }

        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(
                    new MarketplaceItemPostedComposer(MarketplaceItemPostedComposer.MARKETPLACE_DISABLED));
            return;
        }

        if (furniType != 1 && furniType != 2) return;

        List<HabboItem> items = new ArrayList<>();
        for (int itemId : itemIds) {
            if (!MarketplaceInputGuard.isPositiveId(itemId)) return;

            HabboItem item =
                    this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);
            if (item == null) continue;

            if (!item.getBaseItem().allowMarketplace()) {
                String message = Emulator.getTexts()
                        .getValue("scripter.warning.marketplace.forbidden")
                        .replace(
                                "%username%",
                                this.client.getHabbo().getHabboInfo().getUsername())
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
                    .replace("%username%", this.client.getHabbo().getHabboInfo().getUsername())
                    .replace("%itemname%", items.getFirst().getBaseItem().getName())
                    .replace("%credits%", credits + "");
            ScripterManager.scripterDetected(this.client, message);
            LOGGER.info(message);
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        int posted = 0;
        for (HabboItem item : items) {
            if (MarketPlace.sellItem(this.client, item, credits)) posted++;
        }

        this.client.sendResponse(new MarketplaceItemPostedComposer(
                posted > 0
                        ? MarketplaceItemPostedComposer.POST_SUCCESS
                        : MarketplaceItemPostedComposer.FAILED_TECHNICAL_ERROR));

        if (posted > 0) {
            this.client.sendResponse(new MarketplaceOwnItemsComposer(this.client.getHabbo()));
        }
    }
}
