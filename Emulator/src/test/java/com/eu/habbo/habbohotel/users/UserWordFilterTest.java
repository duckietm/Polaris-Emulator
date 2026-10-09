package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UserWordFilterTest {

    @Test
    void normalizeTrimsLowercasesAndCapsAtTheRoomFilterLength() {
        assertEquals("pippo", UserWordFilter.normalize("  PiPPo "));
        assertNull(UserWordFilter.normalize("   "));
        assertNull(UserWordFilter.normalize(null));
        assertEquals(25, UserWordFilter.normalize("a".repeat(40)).length());
    }

    @Test
    void addAndRemoveAreIdempotentAndCaseInsensitive() {
        UserWordFilter filter = new UserWordFilter();

        assertTrue(filter.add("Pippo"));
        assertFalse(filter.add("pippo"));
        assertFalse(filter.add(" "));
        assertTrue(filter.contains("PIPPO"));
        assertEquals(List.of("pippo"), filter.words());

        assertTrue(filter.remove("PIPPO"));
        assertFalse(filter.remove("pippo"));
        assertTrue(filter.isEmpty());
    }

    @Test
    void addStopsAtTheWordCap() {
        UserWordFilter filter = new UserWordFilter();

        for (int i = 0; i < UserWordFilter.MAX_WORDS; i++) {
            assertTrue(filter.add("word" + i));
        }

        assertFalse(filter.add("onemore"));
        assertEquals(UserWordFilter.MAX_WORDS, filter.size());
    }

    @Test
    void applyMasksEveryOccurrenceCaseInsensitivelyAndLeavesOtherTextAlone() {
        UserWordFilter filter = new UserWordFilter();
        filter.add("pippo");
        filter.add("c.d");

        assertEquals("ciao bobba, bobba! c-d bobba", filter.apply("ciao Pippo, PIPPO! c-d c.d", "bobba"));
        assertEquals("nothing here", filter.apply("nothing here", "bobba"));
    }

    @Test
    void applyReturnsTheSameInstanceWhenTheListIsEmpty() {
        UserWordFilter filter = new UserWordFilter();
        String message = "hello";

        assertSame(message, filter.apply(message, "bobba"));
        assertNull(filter.apply(null, "bobba"));
    }

    @Test
    void replacementIsInsertedLiterally() {
        UserWordFilter filter = new UserWordFilter();
        filter.add("x");

        assertEquals("$1\\ $1\\", filter.apply("x x", "$1\\"));
    }
}
