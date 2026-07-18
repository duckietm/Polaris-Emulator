package com.eu.habbo.database.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlowQuerySqlTest {

    @Test
    void removesLiteralValuesAndCommentsBeforeLogging() {
        String first = SlowQuerySql.sanitize(
                "SELECT * /* operator note */ FROM users WHERE username = 'alice' "
                        + "AND password = \"secret\" AND id = 42 -- private\nLIMIT 10",
                512);
        String second = SlowQuerySql.sanitize(
                "SELECT * FROM users WHERE username = 'bob' AND password = \"other\" "
                        + "AND id = 99 LIMIT 20",
                512);

        assertEquals("SELECT * FROM users WHERE username = ? AND password = ? AND id = ? LIMIT ?", first);
        assertEquals(first, second);
        assertFalse(first.contains("alice"));
        assertFalse(first.contains("secret"));
        assertFalse(first.contains("42"));
    }

    @Test
    void keepsIdentifiersAndPlaceholdersWhileBoundingLogSize() {
        String sanitized = SlowQuerySql.sanitize(
                "SELECT `rank_42`, display_name FROM permission_ranks WHERE id = ?   AND enabled = 1",
                64);

        assertTrue(sanitized.startsWith("SELECT `rank_42`, display_name FROM permission_ranks"));
        assertTrue(sanitized.endsWith("..."));
        assertTrue(sanitized.length() <= 64);
    }

    @Test
    void producesTheSameFingerprintForTheSameSanitizedShape() {
        String first = SlowQuerySql.fingerprint("SELECT * FROM users WHERE id = 10");
        String second = SlowQuerySql.fingerprint("SELECT * FROM users WHERE id = 20");

        assertEquals(first, second);
        assertEquals(16, first.length());
    }
}
