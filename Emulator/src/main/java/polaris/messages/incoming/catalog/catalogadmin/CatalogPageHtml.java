package polaris.messages.incoming.catalog.catalogadmin;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * The HTML a catalogue page's texts (headline, teaser, page_text1, page_text2, details, teaser text) may hold when the
 * catalogue admin saves or creates a page: the small set Octane's SanitizeHtml shows. Links go to http(s), mailto, a
 * #fragment, or {@code event:} - a hotel link ({@code event:catalog/open/<page>}, {@code event:habbopages/<page>})
 * that Octane opens inside the client on the layouts that use its internal links. Shared by
 * {@link CatalogAdminSavePageEvent} and {@link CatalogAdminCreatePageEvent}, so both keep the same.
 */
public final class CatalogPageHtml {

    static final Safelist SAFELIST = new Safelist()
            .addTags("b", "i", "u", "br", "span", "div", "p", "a", "strong", "em", "img")
            .addAttributes("a", "href", "target", "class", "style")
            .addAttributes("img", "src", "alt", "class", "style")
            .addAttributes(":all", "class", "style")
            .addProtocols("a", "href", "http", "https", "mailto", "#", "event")
            .addProtocols("img", "src", "http", "https", "data");

    private CatalogPageHtml() {}

    /** The text with only the allowed HTML; empty for none. */
    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Jsoup.clean(value, SAFELIST);
    }
}
