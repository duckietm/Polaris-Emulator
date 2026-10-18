package com.eu.habbo.habbohotel.wired.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WiredLinkDataframeCodecTest {
    @Test
    void decodesTheOfficialRows() {
        assertEquals(List.of(), WiredLinkDataframeCodec.decode("", 0));
        assertEquals(
                List.of(new WiredLinkDataframeCodec.Row("score", ""), new WiredLinkDataframeCodec.Row("msg", "hi")),
                WiredLinkDataframeCodec.decode("[[\"score\",\"\"],[\"msg\",\"hi\"]]", 2));
        assertNull(WiredLinkDataframeCodec.decode("[[\"a\",\"b\"]]", 2));
        assertNull(WiredLinkDataframeCodec.decode("[[\"a\",1]]", 1));
        assertNull(WiredLinkDataframeCodec.decode("{}", 1));
        assertNull(WiredLinkDataframeCodec.decode("[]", 13));
        assertNull(WiredLinkDataframeCodec.decode("not json", 1));
    }

    @Test
    void keysAreNormalizedAndChecked() {
        assertEquals("my_key", WiredLinkDataframeCodec.normalizeKey("  My Key "));
        assertEquals("my_key", WiredLinkDataframeCodec.normalizeKey("_my_key_"));
        assertEquals(WiredLinkDataframeCodec.KEY_LENGTH, WiredLinkDataframeCodec.keyError("   "));
        assertEquals(WiredLinkDataframeCodec.KEY_LENGTH, WiredLinkDataframeCodec.keyError("k".repeat(41)));
        assertEquals(WiredLinkDataframeCodec.NAME_SYNTAX, WiredLinkDataframeCodec.keyError("a-b"));
        assertNull(WiredLinkDataframeCodec.keyError("Points 1"));
    }

    @Test
    void placeholdersFollowTheOfficialRules() {
        assertEquals(
                WiredLinkDataframeCodec.PLACEHOLDER_RESERVED, WiredLinkDataframeCodec.placeholderError("trigger_text"));
        assertEquals(WiredLinkDataframeCodec.PLACEHOLDER_SYNTAX, WiredLinkDataframeCodec.placeholderError("a b"));
        assertEquals(WiredLinkDataframeCodec.PLACEHOLDER_LENGTH, WiredLinkDataframeCodec.placeholderError(""));
        assertNull(WiredLinkDataframeCodec.placeholderError("score"));
    }

    @Test
    void literalsAreWholeNumbersInRange() {
        assertTrue(WiredLinkDataframeCodec.isLiteral("-2147483648"));
        assertFalse(WiredLinkDataframeCodec.isLiteral("2147483648"));
        assertFalse(WiredLinkDataframeCodec.isLiteral("1.5"));
        assertFalse(WiredLinkDataframeCodec.isLiteral(""));
    }

    @Test
    void largeConfigurationsAreRefused() {
        assertEquals(
                WiredLinkDataframeCodec.CONFIGURATION_TOO_LARGE,
                WiredLinkDataframeCodec.storageError("x".repeat(3001), new int[0], new String[0]));
        assertNull(WiredLinkDataframeCodec.storageError("[]", new int[] {0}, new String[12]));
    }
}
