package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationModeTest {
    @Test
    void parsesSupportedModesCaseInsensitively() {
        assertEquals(MigrationMode.VALIDATE, MigrationMode.parse("validate"));
        assertEquals(MigrationMode.APPLY, MigrationMode.parse(" APPLY "));
        assertEquals(MigrationMode.OFF, MigrationMode.parse("off"));
        assertEquals(MigrationMode.VALIDATE, MigrationMode.parse(null));
        assertEquals(MigrationMode.VALIDATE, MigrationMode.parse(""));
    }

    @Test
    void rejectsUnknownMode() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MigrationMode.parse("auto"));

        assertEquals(
                "Unsupported migration mode 'auto'; expected validate, apply or off",
                error.getMessage());
    }
}
