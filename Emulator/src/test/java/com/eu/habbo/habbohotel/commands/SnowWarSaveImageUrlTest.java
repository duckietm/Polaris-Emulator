package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The ad image URL is written into a space and line separated layout, so it must stay a single token. */
class SnowWarSaveImageUrlTest {

    @Test
    void aPlainWebUrlIsKept() {
        assertEquals("https://cdn.test/ads/a.png", SnowWarSaveCommand.safeImageUrl("  https://cdn.test/ads/a.png "));
    }

    @Test
    void whitespaceOrAControlCharacterInsideIsRefused() {
        assertEquals("", SnowWarSaveCommand.safeImageUrl("https://cdn.test/a.png 1 2 3"));
        assertEquals("", SnowWarSaveCommand.safeImageUrl("https://cdn.test/a.png\u0007"));
        assertEquals("", SnowWarSaveCommand.safeImageUrl("https://cdn.test/a.png\tx"));
    }

    @Test
    void anythingButHttpOrHttpsIsRefused() {
        assertEquals("", SnowWarSaveCommand.safeImageUrl("javascript:alert(1)"));
        assertEquals("", SnowWarSaveCommand.safeImageUrl("ftp://cdn.test/a.png"));
        assertEquals("", SnowWarSaveCommand.safeImageUrl(null));
        assertEquals("", SnowWarSaveCommand.safeImageUrl("   "));
    }
}
