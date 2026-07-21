package com.eu.habbo.database.compat;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPermissionsSqlTranslatorTest {

    private static final class FakeContext implements TranslationContext {
        private final boolean normalized;
        private final List<Integer> rankIds;

        private FakeContext(boolean normalized, List<Integer> rankIds) {
            this.normalized = normalized;
            this.rankIds = rankIds;
        }

        @Override
        public boolean isNormalizedPermissionsSchema() {
            return this.normalized;
        }

        @Override
        public List<Integer> rankIds() {
            return this.rankIds;
        }
    }

    private final LegacyPermissionsSqlTranslator translator = new LegacyPermissionsSqlTranslator();
    private final TranslationContext context = new FakeContext(true, List.of(1, 2, 3, 4, 5, 6, 7));

    @Test
    void appliesOnlyToStatementsNamingTheLegacyTable() {
        assertTrue(translator.appliesTo("select * from permissions order by id asc"));
        assertTrue(translator.appliesTo("alter table `permissions` add `cmd_x` enum('0','1')"));
        assertFalse(translator.appliesTo("select * from permission_definitions"));
        assertFalse(translator.appliesTo("select * from permission_ranks"));
        assertFalse(translator.appliesTo("select * from users"));
    }

    @Test
    void inactiveOnLegacySchema() throws SQLException {
        TranslationContext legacy = new FakeContext(false, List.of());
        assertNull(translator.translate("ALTER TABLE `permissions` ADD `cmd_x` ENUM('0','1') NOT NULL DEFAULT '0'", legacy));
    }

    @Test
    void wordguesserStylePermissionRegistrationBecomesDefinitionInsert() throws SQLException {
        // Exact statement shape produced by the WordGuesser example plugin.
        String legacySql = "ALTER TABLE  `permissions` ADD  `cmd_randomword` ENUM(  '0', '1' ) NOT NULL DEFAULT  '0'";

        assertEquals(
                "INSERT INTO permission_definitions (permission_key, max_value, comment) VALUES ('cmd_randomword', 1, 'Registered by a legacy plugin through the Polaris legacy bridge')",
                translator.translate(legacySql, context));
    }

    @Test
    void enumUpperBoundBecomesMaxValue() throws SQLException {
        String translated = translator.translate("ALTER TABLE permissions ADD cmd_x ENUM('0','1','2') NOT NULL DEFAULT '0'", context);

        assertEquals(
                "INSERT INTO permission_definitions (permission_key, max_value, comment) VALUES ('cmd_x', 2, 'Registered by a legacy plugin through the Polaris legacy bridge')",
                translated);
    }

    @Test
    void multipleAddClausesBecomeMultiRowInsert() throws SQLException {
        String translated = translator.translate("ALTER TABLE permissions ADD cmd_a ENUM('0','1'), ADD cmd_b ENUM('0','1','2')", context);

        assertEquals(
                "INSERT INTO permission_definitions (permission_key, max_value, comment) VALUES "
                        + "('cmd_a', 1, 'Registered by a legacy plugin through the Polaris legacy bridge'), "
                        + "('cmd_b', 2, 'Registered by a legacy plugin through the Polaris legacy bridge')",
                translated);
    }

    @Test
    void droppingAPermissionColumnDeletesTheDefinition() throws SQLException {
        assertEquals(
                "DELETE FROM permission_definitions WHERE permission_key IN ('cmd_x')",
                translator.translate("ALTER TABLE `permissions` DROP COLUMN `cmd_x`", context));
    }

    @Test
    void metadataAlterIsRedirectedToPermissionRanks() throws SQLException {
        assertEquals(
                "ALTER TABLE `permission_ranks` ADD `badge` VARCHAR(12) NOT NULL DEFAULT ''",
                translator.translate("ALTER TABLE `permissions` ADD `badge` VARCHAR(12) NOT NULL DEFAULT ''", context));
    }

    @Test
    void soundboardCooldownIsRankMetadata() throws SQLException {
        assertEquals(
                "ALTER TABLE `permission_ranks` ADD COLUMN IF NOT EXISTS `soundboard_cooldown_seconds` INT NOT NULL DEFAULT 60",
                translator.translate(
                        "ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `soundboard_cooldown_seconds` INT NOT NULL DEFAULT 60",
                        context));
    }

    @Test
    void grantUpdateBecomesRankColumnCaseUpdate() throws SQLException {
        String translated = translator.translate("UPDATE permissions SET cmd_randomword = '1' WHERE id >= 6", context);

        assertEquals(
                "UPDATE permission_definitions SET "
                        + "rank_6 = CASE permission_key WHEN 'cmd_randomword' THEN 1 ELSE rank_6 END, "
                        + "rank_7 = CASE permission_key WHEN 'cmd_randomword' THEN 1 ELSE rank_7 END "
                        + "WHERE permission_key IN ('cmd_randomword')",
                translated);
    }

    @Test
    void updateWithoutWhereTargetsAllRanks() throws SQLException {
        TranslationContext twoRanks = new FakeContext(true, List.of(1, 2));
        String translated = translator.translate("UPDATE `permissions` SET `cmd_x` = '1'", twoRanks);

        assertEquals(
                "UPDATE permission_definitions SET "
                        + "rank_1 = CASE permission_key WHEN 'cmd_x' THEN 1 ELSE rank_1 END, "
                        + "rank_2 = CASE permission_key WHEN 'cmd_x' THEN 1 ELSE rank_2 END "
                        + "WHERE permission_key IN ('cmd_x')",
                translated);
    }

    @Test
    void updateMatchingNoRanksBecomesNoOp() throws SQLException {
        assertEquals(
                "UPDATE permission_definitions SET max_value = max_value WHERE 1 = 0",
                translator.translate("UPDATE permissions SET cmd_x = '1' WHERE id = 99", context));
    }

    @Test
    void updateWithInClauseTargetsListedRanks() throws SQLException {
        String translated = translator.translate("UPDATE permissions SET cmd_x = 2 WHERE id IN (2, 5)", context);

        assertEquals(
                "UPDATE permission_definitions SET "
                        + "rank_2 = CASE permission_key WHEN 'cmd_x' THEN 2 ELSE rank_2 END, "
                        + "rank_5 = CASE permission_key WHEN 'cmd_x' THEN 2 ELSE rank_5 END "
                        + "WHERE permission_key IN ('cmd_x')",
                translated);
    }

    @Test
    void metadataUpdateIsRedirectedToPermissionRanks() throws SQLException {
        assertEquals(
                "UPDATE `permission_ranks` SET badge = 'ADM' WHERE id = 7",
                translator.translate("UPDATE permissions SET badge = 'ADM' WHERE id = 7", context));
    }

    @Test
    void updateWithPlaceholderIsLeftAlone() throws SQLException {
        assertNull(translator.translate("UPDATE permissions SET cmd_x = '1' WHERE id = ?", context));
    }

    @Test
    void permissionSelectForARankBecomesPivotedSelect() throws SQLException {
        assertEquals(
                "SELECT MAX(CASE WHEN permission_key = 'cmd_x' THEN rank_5 END) AS `cmd_x` FROM permission_definitions",
                translator.translate("SELECT cmd_x FROM permissions WHERE id = 5", context));
    }

    @Test
    void permissionSelectForUnknownRankReturnsNoRows() throws SQLException {
        assertEquals(
                "SELECT NULL AS `cmd_x` FROM permission_definitions WHERE 1 = 0",
                translator.translate("SELECT cmd_x FROM permissions WHERE id = 42", context));
    }

    @Test
    void selectStarIsRedirectedToPermissionRanks() throws SQLException {
        assertEquals(
                "SELECT * FROM `permission_ranks` ORDER BY id ASC",
                translator.translate("SELECT * FROM permissions ORDER BY id ASC", context));
    }

    @Test
    void metadataSelectIsRedirectedToPermissionRanks() throws SQLException {
        assertEquals(
                "SELECT id, rank_name FROM `permission_ranks` WHERE badge != ''",
                translator.translate("SELECT id, rank_name FROM permissions WHERE badge != ''", context));
    }

    @Test
    void rankDeleteIsRedirectedToPermissionRanks() throws SQLException {
        assertEquals(
                "DELETE FROM `permission_ranks` WHERE id = 7",
                translator.translate("DELETE FROM permissions WHERE id = 7", context));
    }

    @Test
    void metadataInsertIsRedirectedToPermissionRanks() throws SQLException {
        assertEquals(
                "INSERT INTO `permission_ranks` (id, rank_name) VALUES (8, 'Manager')",
                translator.translate("INSERT INTO permissions (id, rank_name) VALUES (8, 'Manager')", context));
    }

    @Test
    void unsupportedShapesPassThroughUntouched() throws SQLException {
        // Mixed metadata + permission columns can't be mapped mechanically.
        assertNull(translator.translate("UPDATE permissions SET badge = 'X', cmd_x = '1' WHERE id = 5", context));
        assertNull(translator.translate("SELECT rank_name, cmd_x FROM permissions WHERE id = 5", context));
        assertNull(translator.translate("ALTER TABLE permissions CHANGE cmd_x cmd_y ENUM('0','1')", context));
        assertNull(translator.translate("INSERT INTO permissions (id, cmd_x) VALUES (8, '1')", context));
    }
}
