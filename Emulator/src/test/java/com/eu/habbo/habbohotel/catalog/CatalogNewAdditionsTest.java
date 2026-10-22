package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AIR catalogue "new additions": the index flags them while the new_additions page offers something newer than the
 * newest offer the player saw there, and MarkCatalogNewAdditionsPageOpened moves the mark to the newest offer.
 */
class CatalogNewAdditionsTest {
    private static final int PLAYER = 9;

    private static CatalogReadFixture.TestPage page(boolean visible, boolean enabled, boolean club, int rank) {
        return CatalogReadFixture.page(
                30,
                -1,
                1,
                visible,
                enabled,
                club,
                rank,
                CatalogNewAdditions.PAGE_NAME,
                "default_3x3",
                CatalogPageType.NORMAL);
    }

    private static CatalogReadFixture.TestPage pageWith(int... offerIds) {
        CatalogReadFixture.TestPage page = page(true, true, false, 1);
        for (int id : offerIds) page.addItem(CatalogReadFixture.item(id, 30, 1));
        return page;
    }

    private static final class Store implements CatalogNewAdditions.Store {
        private final Map<Integer, Integer> marks = new HashMap<>();

        @Override
        public int lastSeen(int userId) {
            return this.marks.getOrDefault(userId, 0);
        }

        @Override
        public void markSeen(int userId, int offerId, int seenAt) {
            this.marks.merge(userId, offerId, Math::max);
        }
    }

    private static CatalogNewAdditions additions(Store store, CatalogPage page) {
        return new CatalogNewAdditions(store) {
            @Override
            protected CatalogPage page(Habbo habbo) {
                return page;
            }
        };
    }

    private static Habbo player(int rank, boolean club) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Rank playerRank = mock(Rank.class);
        HabboStats stats = mock(HabboStats.class);
        when(playerRank.getId()).thenReturn(rank);
        when(info.getId()).thenReturn(PLAYER);
        when(info.getRank()).thenReturn(playerRank);
        when(stats.hasActiveClub()).thenReturn(club);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        return habbo;
    }

    @Test
    void theNewestOfferIsTheHighestCatalogItemId() {
        assertEquals(0, CatalogNewAdditions.newestOfferId(pageWith()));
        assertEquals(812, CatalogNewAdditions.newestOfferId(pageWith(40, 812, 77)));
        assertEquals(0, CatalogNewAdditions.newestOfferId(null));
    }

    @Test
    void onlyAPageThePlayerMayOpenFlagsNewAdditions() {
        assertTrue(CatalogNewAdditions.opensFor(page(true, true, false, 1), 1, false));
        assertFalse(CatalogNewAdditions.opensFor(page(false, true, false, 1), 1, false), "hidden");
        assertFalse(CatalogNewAdditions.opensFor(page(true, false, false, 1), 7, true), "disabled");
        assertFalse(CatalogNewAdditions.opensFor(page(true, true, false, 5), 1, false), "rank");
        assertFalse(CatalogNewAdditions.opensFor(page(true, true, true, 1), 1, false), "club page");
        assertTrue(CatalogNewAdditions.opensFor(page(true, true, true, 1), 1, true));
        assertFalse(
                CatalogNewAdditions.opensFor(
                        CatalogReadFixture.page(
                                30,
                                -1,
                                1,
                                true,
                                true,
                                false,
                                1,
                                "new_additions",
                                "default_3x3",
                                CatalogPageType.BUILDER),
                        1,
                        false),
                "builders club page");
    }

    @Test
    void theFlagLastsUntilThePageIsMarkedAndReturnsWithANewerOffer() {
        Store store = new Store();
        CatalogReadFixture.TestPage page = pageWith(100, 140);
        CatalogNewAdditions additions = additions(store, page);
        Habbo habbo = player(1, false);

        assertTrue(additions.available(habbo), "never seen");

        additions.markOpened(habbo);
        assertEquals(140, store.lastSeen(PLAYER));
        assertFalse(additions.available(habbo), "seen everything");

        page.addItem(CatalogReadFixture.item(141, 30, 2));
        assertTrue(additions.available(habbo), "a newer offer");
    }

    @Test
    void anEmptyOrMissingPageNeverFlags() {
        Habbo habbo = player(1, false);
        assertFalse(additions(new Store(), pageWith()).available(habbo));
        assertFalse(additions(new Store(), null).available(habbo));

        Store store = new Store();
        additions(store, null).markOpened(habbo);
        assertEquals(0, store.lastSeen(PLAYER));
    }

    @Test
    void aPlayerWhoMayNotOpenThePageIsNotFlagged() {
        CatalogReadFixture.TestPage page = page(true, true, true, 1);
        page.addItem(CatalogReadFixture.item(5, 30, 1));

        assertFalse(additions(new Store(), page).available(player(1, false)));
        assertTrue(additions(new Store(), page).available(player(1, true)));
    }
}
