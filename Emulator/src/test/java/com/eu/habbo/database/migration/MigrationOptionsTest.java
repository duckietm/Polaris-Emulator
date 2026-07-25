package com.eu.habbo.database.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationOptionsTest {

    @Test
    void normalStartupUsesConfiguredAutomaticBehavior() {
        MigrationOptions options = MigrationOptions.parse(new String[0]);

        assertEquals(MigrationOptions.Mode.CONFIGURED, options.mode());
        assertFalse(options.migrationsOnly());
    }

    @Test
    void migrationsOnlyForcesApplyWithoutStartingTheHotel() {
        MigrationOptions options = MigrationOptions.parse(new String[]{"--migrations-only"});

        assertEquals(MigrationOptions.Mode.CONFIGURED, options.mode());
        assertTrue(options.migrationsOnly());
    }

    @Test
    void explicitApplyAndValidateModesAreSupported() {
        assertEquals(
                MigrationOptions.Mode.APPLY,
                MigrationOptions.parse(new String[]{"--migrations=apply"}).mode());
        assertEquals(
                MigrationOptions.Mode.VALIDATE,
                MigrationOptions.parse(new String[]{"--migrations=validate"}).mode());
        assertEquals(
                MigrationOptions.Mode.VALIDATE,
                MigrationOptions.parse(new String[]{"--migrations=status"}).mode());
        assertEquals(
                MigrationOptions.Mode.REPAIR,
                MigrationOptions.parse(new String[]{"--migrations=repair"}).mode());
    }

    @Test
    void offRequiresTheVisibleConfigurationSwitch() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MigrationOptions.parse(new String[]{"--migrations=off"}));
    }

    @Test
    void nearMissMigrationOptionsAreRejectedInsteadOfBootingTheHotel() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MigrationOptions.parse(new String[]{"--migration=apply"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> MigrationOptions.parse(new String[]{"--migrations-only=true"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> MigrationOptions.parse(new String[]{"--Migrations=validate"}));
    }
}
