package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewNextLTDAvailableComposer;

public class HotelViewRequestLTDAvailabilityEvent extends MessageHandler {
    public static volatile boolean ENABLED = false;
    public static volatile int TIMESTAMP;
    public static volatile int ITEM_ID;
    public static volatile int PAGE_ID;
    public static volatile String ITEM_NAME;

    @Override
    public void handle() throws Exception {
        if (ENABLED) {
            this.client.sendResponse(availability(Emulator.getIntUnixTimestamp()));
        }
    }

    /**
     * LimitedOfferAppearingNext (44): seconds until the rare, then page id, offer id and product
     * class name (NextLimitedRareCountdownWidget reads them in that order); page and offer stay -1
     * while the countdown runs. Page and item used to be written the other way round, so the
     * "get" button opened the item id as a page.
     */
    public static HotelViewNextLTDAvailableComposer availability(int now) {
        int timeremaining = Math.max(TIMESTAMP - now, -1);
        return new HotelViewNextLTDAvailableComposer(
                timeremaining,
                timeremaining > 0 ? -1 : PAGE_ID,
                timeremaining > 0 ? -1 : ITEM_ID,
                timeremaining > 0 ? "" : ITEM_NAME);
    }
}
