package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalPrefixRemovalMigrationTest {
    @Test
    void deletesPrefixOnlyRulesBeforeDroppingTheirMarker() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V20261002130000__remove_personal_prefixes.sql"));

        int deleteRules = sql.indexOf("DELETE FROM `wordfilter` WHERE `prefix_only` = ''1''");
        int dropMarker = sql.indexOf("DROP COLUMN IF EXISTS `prefix_only`");
        assertTrue(deleteRules >= 0 && dropMarker > deleteRules);
        assertTrue(sql.contains("AND `COLUMN_NAME` = 'prefix_only'"));
    }
}
