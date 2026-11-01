package com.eu.habbo.database.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyTableRenameTranslatorTest {

    private static final TranslationContext CONTEXT = new TranslationContext() {
        @Override
        public boolean isNormalizedPermissionsSchema() {
            return true;
        }

        @Override
        public List<Integer> rankIds() {
            return List.of();
        }
    };

    @Test
    void renamesTablesInTablePosition() {
        LegacyTableRenameTranslator translator =
                new LegacyTableRenameTranslator(() -> "old_words:random_words;old_logs:new_logs");

        assertTrue(translator.appliesTo("select * from `old_words`"));
        assertEquals(
                "SELECT * FROM `random_words` WHERE word = 'old_words value'",
                translator.translate("SELECT * FROM `old_words` WHERE word = 'old_words value'", CONTEXT));
        assertEquals(
                "INSERT INTO `new_logs` (message) VALUES ('x')",
                translator.translate("INSERT INTO old_logs (message) VALUES ('x')", CONTEXT));
        assertEquals(
                "UPDATE `random_words` SET word = 'a'",
                translator.translate("UPDATE old_words SET word = 'a'", CONTEXT));
    }

    @Test
    void leavesTableNamesInsideStringsAlone() {
        LegacyTableRenameTranslator translator = new LegacyTableRenameTranslator(() -> "old_words:random_words");

        assertEquals(
                "INSERT INTO `random_words` (word) VALUES ('copied from old_words', \"into old_words\")",
                translator.translate(
                        "INSERT INTO old_words (word) VALUES ('copied from old_words', \"into old_words\")", CONTEXT));
        assertEquals(
                "SELECT 'it''s from old_words' FROM `random_words`",
                translator.translate("SELECT 'it''s from old_words' FROM old_words", CONTEXT));
        assertNull(translator.translate("SELECT 'from old_words'", CONTEXT));
    }

    @Test
    void leavesColumnNamesAlone() {
        LegacyTableRenameTranslator translator = new LegacyTableRenameTranslator(() -> "word:renamed_table");

        // "word" only appears as a column here, never in table position.
        assertNull(translator.translate("SELECT word FROM random_words", CONTEXT));
    }

    @Test
    void emptyOrMalformedConfigDisablesTheTranslator() {
        assertFalse(new LegacyTableRenameTranslator(() -> "").appliesTo("select * from old_words"));
        assertFalse(new LegacyTableRenameTranslator(() -> "not-a-mapping").appliesTo("select * from old_words"));
        assertFalse(new LegacyTableRenameTranslator(() -> "bad name:new;:x").appliesTo("select * from old_words"));
    }

    @Test
    void configChangesArePickedUpWithoutRestart() {
        String[] value = {"a_old:a_new"};
        LegacyTableRenameTranslator translator = new LegacyTableRenameTranslator(() -> value[0]);

        assertEquals("SELECT * FROM `a_new`", translator.translate("SELECT * FROM a_old", CONTEXT));

        value[0] = "a_old:a_newer";
        assertEquals("SELECT * FROM `a_newer`", translator.translate("SELECT * FROM a_old", CONTEXT));
    }
}
