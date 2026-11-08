package polaris.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CatalogPageHtmlTest {

    @Test
    void keepsHotelLinks() {
        assertEquals(
                "<a href=\"event:catalog/open/rares\">Rares</a>",
                CatalogPageHtml.sanitize("<a href=\"event:catalog/open/rares\">Rares</a>"));
        assertEquals(
                "<a href=\"event:habbopages/help\">Help</a>",
                CatalogPageHtml.sanitize("<a href=\"event:habbopages/help\">Help</a>"));
    }

    @Test
    void keepsWebLinksAndFormatting() {
        assertEquals(
                "<b>Hi</b> <a href=\"https://hotel.example.com\">site</a>",
                CatalogPageHtml.sanitize("<b>Hi</b> <a href=\"https://hotel.example.com\">site</a>"));
        assertEquals("<a href=\"#top\">up</a>", CatalogPageHtml.sanitize("<a href=\"#top\">up</a>"));
    }

    @Test
    void dropsScriptsHandlersAndUnsafeLinks() {
        assertEquals("<b>Hi</b>", CatalogPageHtml.sanitize("<b onclick=\"steal()\">Hi</b><script>alert(1)</script>"));
        assertEquals("<a>x</a>", CatalogPageHtml.sanitize("<a href=\"javascript:alert(1)\">x</a>"));
        assertEquals("<img>", CatalogPageHtml.sanitize("<img src=\"event:x\">"), "an image never comes from event:");
    }

    @Test
    void emptyStaysEmpty() {
        assertEquals("", CatalogPageHtml.sanitize(null));
        assertEquals("", CatalogPageHtml.sanitize(""));
    }
}
