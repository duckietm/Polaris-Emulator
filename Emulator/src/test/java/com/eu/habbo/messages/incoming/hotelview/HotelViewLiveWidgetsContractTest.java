package com.eu.habbo.messages.incoming.hotelview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.messages.incoming.catalog.GetSeasonalCalendarDailyOfferEvent;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewNextLTDAvailableComposer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The hotel view widgets and the seasonal calendar read these packets field by field. */
class HotelViewLiveWidgetsContractTest {
    private int timestamp;
    private int itemId;
    private int pageId;
    private String itemName;

    @BeforeEach
    void remember() {
        this.timestamp = HotelViewRequestLTDAvailabilityEvent.TIMESTAMP;
        this.itemId = HotelViewRequestLTDAvailabilityEvent.ITEM_ID;
        this.pageId = HotelViewRequestLTDAvailabilityEvent.PAGE_ID;
        this.itemName = HotelViewRequestLTDAvailabilityEvent.ITEM_NAME;
    }

    @AfterEach
    void restore() {
        HotelViewRequestLTDAvailabilityEvent.TIMESTAMP = this.timestamp;
        HotelViewRequestLTDAvailabilityEvent.ITEM_ID = this.itemId;
        HotelViewRequestLTDAvailabilityEvent.PAGE_ID = this.pageId;
        HotelViewRequestLTDAvailabilityEvent.ITEM_NAME = this.itemName;
    }

    @Test
    void theNextLimitedRareCarriesThePageBeforeTheOffer() {
        HotelViewRequestLTDAvailabilityEvent.TIMESTAMP = 1000;
        HotelViewRequestLTDAvailabilityEvent.ITEM_ID = 77;
        HotelViewRequestLTDAvailabilityEvent.PAGE_ID = 12;
        HotelViewRequestLTDAvailabilityEvent.ITEM_NAME = "rare_dragonlamp";

        HotelViewNextLTDAvailableComposer released = HotelViewRequestLTDAvailabilityEvent.availability(2000);
        assertEquals(-1, released.getTime());
        assertEquals(12, released.getPageId());
        assertEquals(77, released.getItemId());
        assertEquals("rare_dragonlamp", released.getItemName());

        HotelViewNextLTDAvailableComposer pending = HotelViewRequestLTDAvailabilityEvent.availability(400);
        assertEquals(600, pending.getTime());
        assertEquals(-1, pending.getPageId());
        assertEquals(-1, pending.getItemId());
        assertEquals("", pending.getItemName());
    }

    @Test
    void theSeasonalCalendarOfferCyclesOneItemPerDay() {
        int day = 20000 * 86400;
        assertEquals(0, GetSeasonalCalendarDailyOfferEvent.offerOfTheDay("", day));
        assertEquals(12, GetSeasonalCalendarDailyOfferEvent.offerOfTheDay("10, 11, 12", day + 3600));
        assertEquals(10, GetSeasonalCalendarDailyOfferEvent.offerOfTheDay("10, 11, 12", day + 86400));
        assertEquals(11, GetSeasonalCalendarDailyOfferEvent.offerOfTheDay("10, 11, 12", day + 2 * 86400));
        assertEquals(0, GetSeasonalCalendarDailyOfferEvent.offerOfTheDay("x", day));
    }
}
