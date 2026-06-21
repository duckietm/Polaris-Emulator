package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageLayouts;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.*;
import com.eu.habbo.messages.outgoing.users.ClubGiftReceivedComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class CatalogSelectClubGiftEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSelectClubGiftEvent.class);

    @Override
    public void handle() throws Exception {

        String itemName = this.packet.readString();

        if(itemName.isEmpty()) {
            LOGGER.error("itemName is empty");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if(this.client.getHabbo().getHabboStats().getRemainingClubGifts() < 1) {
            LOGGER.error("User has no remaining club gifts");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPageByLayout(CatalogPageLayouts.club_gift.name().toLowerCase());

        if(page == null) {
            LOGGER.error("Catalog page not found");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        CatalogItem catalogItem = page.getCatalogItems().values().stream().filter(x -> x.getName().equalsIgnoreCase(itemName)).findAny().orElse(null);

        if(catalogItem == null) {
            LOGGER.error("Catalog item not found");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        int daysRequired = 0;
        try {
            daysRequired = Integer.parseInt(catalogItem.getExtradata());
        }
        catch (NumberFormatException ignored) { }

        if(daysRequired > (int) Math.floor(this.client.getHabbo().getHabboStats().getPastTimeAsClub() / 86400.0)) {
            LOGGER.error("Not been member for long enough");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        Set<Item> itemsGiven = new HashSet<>();
        for(Item item : catalogItem.getBaseItems()) {
            if(Emulator.getGameEnvironment().getItemManager().createGift(this.client.getHabbo().getHabboInfo().getId(), item, "", 0, 0) != null) {
                itemsGiven.add(item);
            }
        }

        this.client.getHabbo().getHabboStats().hcGiftsClaimed++;
        Emulator.getThreading().run(this.client.getHabbo().getHabboStats());

        this.client.sendResponse(new ClubGiftReceivedComposer(itemName, itemsGiven));

    }
}
