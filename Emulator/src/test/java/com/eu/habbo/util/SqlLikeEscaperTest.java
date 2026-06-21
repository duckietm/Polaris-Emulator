package com.eu.habbo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlLikeEscaperTest {

    @Test
    void nullBecomesEmptyString() {
        assertEquals("", SqlLikeEscaper.escape(null));
    }

    @Test
    void plainInputIsUnchanged() {
        assertEquals("Alice", SqlLikeEscaper.escape("Alice"));
    }

    @Test
    void wildcardsAreEscaped() {
        assertEquals("100\\% real", SqlLikeEscaper.escape("100% real"));
        assertEquals("user\\_name", SqlLikeEscaper.escape("user_name"));
    }

    @Test
    void backslashIsEscapedFirstToAvoidDoubleEscaping() {
        // The escape char itself must be doubled before %/_ are escaped,
        // otherwise the inserted backslashes would be escaped again.
        assertEquals("a\\\\b", SqlLikeEscaper.escape("a\\b"));
        assertEquals("\\\\\\%", SqlLikeEscaper.escape("\\%"));
    }
}
