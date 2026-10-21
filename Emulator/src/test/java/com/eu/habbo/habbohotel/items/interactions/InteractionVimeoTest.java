package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InteractionVimeoTest {
    @Test
    void vimeoVideoIdsAreNumbers() {
        assertTrue(InteractionVimeo.isValidVideoId("0"));
        assertTrue(InteractionVimeo.isValidVideoId("40036380"));
        assertFalse(InteractionVimeo.isValidVideoId(""));
        assertFalse(InteractionVimeo.isValidVideoId(null));
        assertFalse(InteractionVimeo.isValidVideoId("12a"));
        assertFalse(InteractionVimeo.isValidVideoId("-5"));
        assertFalse(InteractionVimeo.isValidVideoId("١٢"));
        assertFalse(InteractionVimeo.isValidVideoId("1".repeat(InteractionVimeo.MAX_VIDEO_ID_DIGITS + 1)));
    }
}
