package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import polaris.hotel.catalog.BundleOfferReferences.Content;
import polaris.hotel.catalog.BundleOfferReferences.Expansion;

/** A bundle that names catalogue offers: what it hands out. */
class BundleOfferReferencesTest {
    private final Map<Integer, Content> offers = new HashMap<>();

    private Expansion expand(String itemIds) {
        return BundleOfferReferences.expand(itemIds, 1, this.offers::get);
    }

    @Test
    void knowsAListThatNamesAnOffer() {
        assertTrue(BundleOfferReferences.hasReferences("4501;@12"));
        assertFalse(BundleOfferReferences.hasReferences("4501:2;4502"));
        assertFalse(BundleOfferReferences.hasReferences(null));
    }

    @Test
    void aNamedOfferGivesItsFurniInItsAmount() {
        this.offers.put(12, new Content("4501", 3));

        assertEquals("4501:3;", this.expand("@12").itemIds());
    }

    @Test
    void anAmountOnTheNameMultiplies() {
        this.offers.put(12, new Content("4501", 3));

        assertEquals("4501:6;", this.expand("@12:2").itemIds());
    }

    @Test
    void offersAndPlainFurniStandTogetherAndTheSameFurniAddsUp() {
        this.offers.put(12, new Content("4501", 1));
        this.offers.put(13, new Content("4502:2;4501", 1));

        assertEquals("4501:3;4502:2;4600:1;", this.expand("@12;@13;4501;4600").itemIds());
    }

    @Test
    void aNamedOfferMayNameOffers() {
        this.offers.put(12, new Content("@13:2;4700", 1));
        this.offers.put(13, new Content("4502", 1));

        assertEquals("4502:2;4700:1;", this.expand("@12").itemIds());
    }

    @Test
    void anUnknownOfferIsLeftOutAndReported() {
        this.offers.put(12, new Content("4501", 1));

        Expansion expansion = this.expand("@12;@99");

        assertEquals("4501:1;", expansion.itemIds());
        assertEquals(List.of(99), expansion.missing());
    }

    @Test
    void aLoopEndsAndIsReported() {
        this.offers.put(12, new Content("@13;4501", 1));
        this.offers.put(13, new Content("@12;4502", 1));

        Expansion expansion = this.expand("@12");

        assertEquals("4502:1;4501:1;", expansion.itemIds());
        assertEquals(List.of(12), expansion.missing());
    }

    @Test
    void nothingUsableIsAnEmptyBundle() {
        assertEquals("", this.expand("@99;@x;@0").itemIds());
    }

    @Test
    void amountsStayWithinTheLimit() {
        this.offers.put(12, new Content("4501", 900));

        assertEquals(
                "4501:" + BundleOfferReferences.MAX_AMOUNT + ";",
                this.expand("@12:50").itemIds());
    }
}
