package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationCatalogTest {
    @Test
    void ordersResourcesByNumericVersionAndCalculatesStableChecksum() {
        MigrationCatalog catalog = MigrationCatalog.fromResources(resources(
                "028_next.sql", "SELECT 28;",
                "001_first.sql", "SELECT 1;",
                "027_history.sql", "SELECT 27;",
                "002_second.sql", "SELECT 2;"));

        assertEquals(List.of(1, 2, 27, 28), catalog.migrations().stream()
                .map(MigrationDescriptor::version)
                .toList());
        MigrationDescriptor next = catalog.migrations().getLast();
        assertEquals("next", next.description());
        assertEquals("028_next.sql", next.scriptName());
        assertTrue(next.checksumSha256().matches("[0-9a-f]{64}"));
        assertEquals(next.checksumSha256(), MigrationCatalog.fromResources(resources(
                "028_next.sql", "SELECT 28;")).migrations().getFirst().checksumSha256());
    }

    @Test
    void acceptsMixedCaseOnlyForHistoricalBaselineResources() {
        MigrationCatalog catalog = MigrationCatalog.fromResources(resources(
                "005_HC_Allow_Gifts.sql", "SELECT 5;",
                "027_client_release_contract.sql", "SELECT 27;"));

        assertEquals(List.of(5, 27), catalog.migrations().stream()
                .map(MigrationDescriptor::version)
                .toList());
        assertThrows(MigrationValidationException.class, () -> MigrationCatalog.fromResources(resources(
                "028_Not_Lowercase.sql", "SELECT 28;")));
    }

    @Test
    void rejectsDuplicateVersionsMalformedNamesAndFutureGaps() {
        assertThrows(MigrationValidationException.class, () -> MigrationCatalog.fromResources(resources(
                "028_one.sql", "SELECT 1;",
                "028_duplicate.sql", "SELECT 2;")));
        assertThrows(MigrationValidationException.class, () -> MigrationCatalog.fromResources(resources(
                "V028_wrong.sql", "SELECT 1;")));
        assertThrows(MigrationValidationException.class, () -> MigrationCatalog.fromResources(resources(
                "027_history.sql", "SELECT 27;",
                "028_one.sql", "SELECT 28;",
                "030_gap.sql", "SELECT 30;")));
    }

    @Test
    void rejectsZeroVersionWithTheOffendingFilename() {
        MigrationValidationException error = assertThrows(
                MigrationValidationException.class,
                () -> MigrationCatalog.fromResources(resources("000_invalid.sql", "SELECT 0;")));

        assertTrue(error.getMessage().contains("000_invalid.sql"));
    }

    @Test
    void acceptsHistoricalVersionGapsButRequiresFutureToStartAtTwentyEight() {
        assertEquals(List.of(1, 12, 15, 27), MigrationCatalog.fromResources(resources(
                "001_first.sql", "SELECT 1;",
                "012_twelve.sql", "SELECT 12;",
                "015_fifteen.sql", "SELECT 15;",
                "027_history.sql", "SELECT 27;")).migrations().stream()
                .map(MigrationDescriptor::version)
                .toList());

        assertThrows(MigrationValidationException.class, () -> MigrationCatalog.fromResources(resources(
                "027_history.sql", "SELECT 27;",
                "029_skips_first_managed.sql", "SELECT 29;")));
    }

    private static Map<String, String> resources(String... namesAndSql) {
        Map<String, String> resources = new LinkedHashMap<>();
        for (int index = 0; index < namesAndSql.length; index += 2) {
            resources.put(namesAndSql[index], namesAndSql[index + 1]);
        }
        return resources;
    }
}
