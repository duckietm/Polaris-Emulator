package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.unknown.UnknownCatalogPageOfferComposer;

/**
 * GetSeasonalCalendarDailyOffer (3257): the catalog promo of the seasonal quest calendar asks for
 * the offer of the day and gets SeasonalCalendarDailyOffer (1889, page id + catalog offer) back.
 *
 * <p>The offers are the catalog item ids in {@code seasonalcalendar.dailyoffer.items} (comma list);
 * one is picked per day, cycling through the list. Without a list nothing is sent and the client
 * keeps the buy button disabled, as the official client does before the answer arrives.
 */
public class GetSeasonalCalendarDailyOfferEvent extends MessageHandler {
    public static final String ITEMS_KEY = "seasonalcalendar.dailyoffer.items";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        int itemId =
                offerOfTheDay(Emulator.getConfig().getValue(ITEMS_KEY, ""), (int) (System.currentTimeMillis() / 1000L));
        if (itemId <= 0) {
            return;
        }

        CatalogItem item = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(itemId);
        if (item == null) {
            return;
        }

        this.client.sendResponse(new UnknownCatalogPageOfferComposer(item.getPageId(), item));
    }

    /** The catalog item id of the day: day {@code n} since the epoch takes entry {@code n % size}. */
    public static int offerOfTheDay(String items, int timestamp) {
        if (items == null || items.isBlank()) {
            return 0;
        }

        String[] entries = items.split(",");
        int day = Math.floorDiv(timestamp, 86400);
        String entry = entries[Math.floorMod(day, entries.length)].trim();

        try {
            return Integer.parseInt(entry);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
