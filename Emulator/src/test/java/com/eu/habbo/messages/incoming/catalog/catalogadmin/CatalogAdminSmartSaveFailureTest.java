package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogConcurrentModificationException;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class CatalogAdminSmartSaveFailureTest {

    private static final Gson GSON = new Gson();

    /**
     * A defect in the save path must still answer the operator. The editor shows nothing at all when
     * the handler throws, because the packet loop logs the exception and sends no response.
     */
    @Test
    void answersEvenWhenTheFailureCarriesNoMessage() {
        assertNotNull(assertDoesNotThrow(() -> failure(new NullPointerException())));
    }

    @Test
    void answersOnAnUnexpectedRuntimeFailure() {
        assertNotNull(assertDoesNotThrow(() -> failure(new IllegalStateException("live catalog is closed"))));
    }

    @Test
    void stillAnswersOnTheExpectedRejections() {
        assertNotNull(assertDoesNotThrow(() -> failure(new IllegalArgumentException("Offer not found: 42"))));
        assertNotNull(assertDoesNotThrow(() -> failure(new CatalogConcurrentModificationException(12L, 4L))));
    }

    private static Object failure(RuntimeException exception) {
        return CatalogAdminSmartSaveResponder.failure(
                "saveOffer-test-1", "saveOffer", 12L, 4L, "OFFER", "NORMAL", 42, exception, GSON);
    }
}
