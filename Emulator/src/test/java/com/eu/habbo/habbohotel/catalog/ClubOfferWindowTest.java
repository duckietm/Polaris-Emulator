package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * The offer windows AIR asks for: club_buy 0 and vip_buy 1 list the Habbo Club and VIP offers, loyalty_vip_buy 6 the
 * ones of those paid in points only, 2 and 3 stay the Builders Club membership and add-ons.
 */
class ClubOfferWindowTest {

    @Test
    void clubBuyAndVipBuyListTheHabboClubAndVipOffers() throws Exception {
        for (String type : new String[] {"HC", "VIP"}) {
            ClubOffer offer = offer(type, 25, 0, 0);

            assertTrue(offer.belongsToWindow(ClubOffer.WINDOW_CLUB_BUY, -1), type);
            assertTrue(offer.belongsToWindow(ClubOffer.WINDOW_HABBO_CLUB, -1), type);
        }

        ClubOffer builders = offer("BUILDERS_CLUB", 25, 0, 0);
        assertFalse(builders.belongsToWindow(ClubOffer.WINDOW_CLUB_BUY, -1));
        assertFalse(builders.belongsToWindow(ClubOffer.WINDOW_HABBO_CLUB, -1));
        assertTrue(builders.belongsToWindow(ClubOffer.WINDOW_BUILDERS_CLUB, -1));
        assertTrue(offer("BUILDERS_CLUB_ADDON", 25, 0, 0).belongsToWindow(ClubOffer.WINDOW_BUILDERS_CLUB_ADDONS, -1));
    }

    @Test
    void theLoyaltyWindowHoldsTheClubOffersPaidInPointsOnly() throws Exception {
        assertTrue(offer("VIP", 0, 500, 105).belongsToWindow(ClubOffer.WINDOW_LOYALTY_VIP, -1));
        assertTrue(offer("HC", 0, 500, 5).belongsToWindow(ClubOffer.WINDOW_LOYALTY_VIP, -1));
        assertFalse(offer("VIP", 25, 0, 0).belongsToWindow(ClubOffer.WINDOW_LOYALTY_VIP, -1));
        assertFalse(offer("VIP", 25, 500, 105).belongsToWindow(ClubOffer.WINDOW_LOYALTY_VIP, -1));
        assertFalse(offer("BUILDERS_CLUB", 0, 500, 105).belongsToWindow(ClubOffer.WINDOW_LOYALTY_VIP, -1));
    }

    @Test
    void aConfiguredLoyaltyPointsTypeNarrowsTheLoyaltyWindow() throws Exception {
        assertTrue(offer("VIP", 0, 500, 105).belongsToWindow(ClubOffer.WINDOW_LOYALTY_VIP, 105));
        assertFalse(offer("VIP", 0, 500, 5).belongsToWindow(ClubOffer.WINDOW_LOYALTY_VIP, 105));
    }

    @Test
    void theOneArgumentFormKeepsItsAnswersWithoutSettings() throws Exception {
        assertTrue(offer("HC", 25, 0, 0).belongsToWindow(ClubOffer.WINDOW_HABBO_CLUB));
        assertTrue(offer("HC", 25, 0, 0).belongsToWindow(ClubOffer.WINDOW_CLUB_BUY));
        assertTrue(offer("VIP", 0, 500, 105).belongsToWindow(ClubOffer.WINDOW_LOYALTY_VIP));
    }

    private static ClubOffer offer(String type, int credits, int points, int pointsType) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(1);
        when(set.getString("name")).thenReturn("offer");
        when(set.getInt("days")).thenReturn(31);
        when(set.getInt("credits")).thenReturn(credits);
        when(set.getInt("points")).thenReturn(points);
        when(set.getInt("points_type")).thenReturn(pointsType);
        when(set.getString("type")).thenReturn(type);
        when(set.getString("deal")).thenReturn("0");
        when(set.getString("giftable")).thenReturn("0");
        return new ClubOffer(set);
    }
}
