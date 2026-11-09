package polaris.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.catalog.CatalogPage;

/**
 * A catalogue search answered offers of any page: furni of a staff, club or disabled page showed up, and only the
 * purchase was refused. The search now answers only what the user may buy, with the purchase's own rule.
 */
class CatalogSearchedItemAccessTest {

    private static CatalogPage page(boolean enabled, int rank, boolean clubOnly) {
        CatalogPage page = mock(CatalogPage.class);
        when(page.isEnabled()).thenReturn(enabled);
        when(page.getRank()).thenReturn(rank);
        when(page.isClubOnly()).thenReturn(clubOnly);
        return page;
    }

    @Test
    void showsAnOfferOfAnOpenPage() {
        assertTrue(CatalogSearchedItemEvent.mayShow(page(true, 1, false), 1, false));
    }

    @Test
    void hidesAnOfferOfAPageAboveTheUsersRank() {
        assertFalse(CatalogSearchedItemEvent.mayShow(page(true, 7, false), 1, false));
        assertTrue(CatalogSearchedItemEvent.mayShow(page(true, 7, false), 7, false));
    }

    @Test
    void hidesAnOfferOfAClubPageWithoutClub() {
        assertFalse(CatalogSearchedItemEvent.mayShow(page(true, 1, true), 1, false));
        assertTrue(CatalogSearchedItemEvent.mayShow(page(true, 1, true), 1, true));
    }

    @Test
    void hidesAnOfferOfADisabledPage() {
        assertFalse(CatalogSearchedItemEvent.mayShow(page(false, 1, false), 7, true));
    }
}
