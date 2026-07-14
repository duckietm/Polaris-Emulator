package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptSplitterTest {
    @Test
    void splitsOrdinaryStatementsAndKeepsTrailingStatement() {
        assertEquals(
                List.of("CREATE TABLE sample (id INT)", "INSERT INTO sample VALUES (1)", "SELECT 1"),
                SqlScriptSplitter.split("CREATE TABLE sample (id INT);\n"
                        + "INSERT INTO sample VALUES (1);\n"
                        + "SELECT 1"));
    }

    @Test
    void ignoresSemicolonsInsideQuotedValuesAndIdentifiers() {
        assertEquals(
                List.of("INSERT INTO `odd;table` VALUES ('one;two', \"three;four\")", "SELECT 2"),
                SqlScriptSplitter.split(
                        "INSERT INTO `odd;table` VALUES ('one;two', \"three;four\"); SELECT 2;"));
    }

    @Test
    void supportsBackslashEscapesAndDoubledQuoteEscapes() {
        String sql = "INSERT INTO sample VALUES ('it\\'s;fine', 'it''s;also fine', "
                + "\"a\\\";b\", \"a\"\";b\", `a``;b`); SELECT 3;";

        assertEquals(List.of(
                "INSERT INTO sample VALUES ('it\\'s;fine', 'it''s;also fine', "
                        + "\"a\\\";b\", \"a\"\";b\", `a``;b`)",
                "SELECT 3"), SqlScriptSplitter.split(sql));
    }

    @Test
    void ignoresSemicolonsInsideLineAndBlockComments() {
        String sql = "-- first; comment\nSELECT 1; # second; comment\r\n"
                + "/* block; comment */ SELECT 2;";

        assertEquals(List.of(
                "-- first; comment\nSELECT 1",
                "# second; comment\r\n/* block; comment */ SELECT 2"),
                SqlScriptSplitter.split(sql));
    }

    @Test
    void omitsWhitespaceAndCommentOnlyFragments() {
        assertEquals(List.of(), SqlScriptSplitter.split(" ; -- comment only;\n /* block; only */ ; \n"));
    }

    @Test
    void rejectsDelimiterDirectiveAtCaseInsensitiveLineStart() {
        for (String sql : List.of(
                "DELIMITER $$\nCREATE PROCEDURE p() SELECT 1$$",
                "  delimiter //\nSELECT 1//",
                "SELECT 1;\n\tDeLiMiTeR |")) {
            MigrationValidationException error = assertThrows(
                    MigrationValidationException.class,
                    () -> SqlScriptSplitter.split(sql));
            assertTrue(error.getMessage().contains("DELIMITER"));
        }
    }

    @Test
    void allowsDelimiterWordOutsideDirectivePosition() {
        assertEquals(
                List.of("SELECT 'DELIMITER $$'", "SELECT delimiter FROM settings"),
                SqlScriptSplitter.split("SELECT 'DELIMITER $$'; SELECT delimiter FROM settings;"));
    }

    @Test
    void rejectsUnterminatedQuotesAndBlockComments() {
        for (String sql : List.of("SELECT 'open", "SELECT \"open", "SELECT `open", "SELECT 1 /* open")) {
            assertThrows(MigrationValidationException.class, () -> SqlScriptSplitter.split(sql));
        }
    }

    @Test
    void splitsEveryPackagedHistoricalMigration() {
        MigrationCatalog catalog = MigrationCatalog.load(getClass().getClassLoader());

        for (MigrationDescriptor migration : catalog.migrations()) {
            assertTrue(
                    !SqlScriptSplitter.split(migration.sql()).isEmpty(),
                    () -> "Expected executable SQL in " + migration.scriptName());
        }
    }
}
