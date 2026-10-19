package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExactUsernameMatchTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/" + path));
    }

    @Test
    void unbanMatchesTheUsernameExactly() throws Exception {
        String source = source("modtool/ModToolManager.java");

        assertTrue(source.contains("WHERE users.username = ? AND ban_expire > ?"));
        assertFalse(source.contains("users.username LIKE ? AND ban_expire"), "_ and % in a name are wildcards in LIKE");
    }

    @Test
    void allowTradingMatchesTheUsernameExactly() throws Exception {
        String source = source("commands/AllowTradingCommand.java");

        assertTrue(source.contains("WHERE users.username = ?"));
        assertFalse(source.contains("users.username LIKE ?"), "_ and % in a name are wildcards in LIKE");
    }
}
