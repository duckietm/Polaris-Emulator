package polaris.messages.incoming.catalog;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageLayouts;
import polaris.hotel.items.Item;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.users.ClubGiftReceivedComposer;

public class CatalogSelectClubGiftEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSelectClubGiftEvent.class);

    @Override
    public void handle() throws Exception {

        String itemName = this.packet.readString();

        if (itemName.isEmpty()) {
            LOGGER.error("itemName is empty");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (this.client.getUser().getUserStats().getRemainingClubGifts() < 1) {
            LOGGER.error("User has no remaining club gifts");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        CatalogPage page = Emulator.getGameEnvironment()
                .getCatalogManager()
                .getCatalogPageByLayout(CatalogPageLayouts.club_gift.name().toLowerCase());

        if (page == null) {
            LOGGER.error("Catalog page not found");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        CatalogItem catalogItem = page.getCatalogItems().values().stream()
                .filter(x -> x.getName().equalsIgnoreCase(itemName))
                .findAny()
                .orElse(null);

        if (catalogItem == null) {
            LOGGER.error("Catalog item not found");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        int daysRequired = 0;
        try {
            daysRequired = Integer.parseInt(catalogItem.getExtradata());
        } catch (NumberFormatException ignored) {
        }

        if (daysRequired > (int) Math.floor(this.client.getUser().getUserStats().getPastTimeAsClub() / 86400.0)) {
            LOGGER.error("Not been member for long enough");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        Set<Item> itemsGiven = new HashSet<>();
        for (Item item : catalogItem.getBaseItems()) {
            if (Emulator.getGameEnvironment()
                            .getItemManager()
                            .createGift(this.client.getUser().getUserInfo().getId(), item, "", 0, 0)
                    != null) {
                itemsGiven.add(item);
            }
        }

        // A claim is spent only when something was given: a gift that could not be created used to cost one anyway.
        if (itemsGiven.isEmpty()) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        this.client.getUser().getUserStats().hcGiftsClaimed++;
        Emulator.getThreading().run(this.client.getUser().getUserStats());

        this.client.sendResponse(new ClubGiftReceivedComposer(itemName, itemsGiven));
    }
}
