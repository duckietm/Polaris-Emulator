package polaris.hotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** An undo only goes ahead while the rows still hold what the operation left them with. */
class CatalogUndoDriftTest {

    @Test
    void aRowThatStillHoldsTheAfterStateMatches() {
        assertTrue(CatalogUndoDrift.matches(
                "{\"offerId\":5,\"catalogName\":\"chair\",\"costCredits\":3}",
                JsonParser.parseString("{\"offerId\":5,\"catalogName\":\"chair\",\"costCredits\":3.0,\"songId\":0}")));
    }

    @Test
    void aRowEditedSinceDoesNotMatch() {
        assertFalse(CatalogUndoDrift.matches(
                "{\"offerId\":5,\"catalogName\":\"chair\",\"costCredits\":3}",
                JsonParser.parseString("{\"offerId\":5,\"catalogName\":\"chair\",\"costCredits\":10}")));
    }

    @Test
    void emptyAndMissingTextAreTheSame() {
        assertTrue(CatalogUndoDrift.matches(
                "{\"pageId\":2,\"pageText1\":\"\"}", JsonParser.parseString("{\"pageId\":2}")));
    }

    @Test
    void aDeletedRowMatchesOnlyWhileItIsGone() {
        assertTrue(CatalogUndoDrift.matches(null, null));
        assertFalse(CatalogUndoDrift.matches(null, JsonParser.parseString("{\"pageId\":2}")));
        assertFalse(CatalogUndoDrift.matches("{\"pageId\":2}", null));
    }
}
