package com.eu.habbo.database.compat;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A plugin statement on the old permissions table that the bridge does not understand ran silently against a table
 * nothing reads any more. It still runs unchanged, but the operator is told once.
 */
class LegacyPermissionsUntranslatedWarningTest {

    private static final TranslationContext NORMALIZED = new TranslationContext() {
        @Override
        public boolean isNormalizedPermissionsSchema() {
            return true;
        }

        @Override
        public List<Integer> rankIds() {
            return List.of(1, 2, 3);
        }
    };

    @Test
    void anUnderstoodStatementIsStillPassedThroughUnchanged() throws Exception {
        LegacyPermissionsSqlTranslator translator = new LegacyPermissionsSqlTranslator();

        assertNull(translator.translate("UPDATE permissions SET cmd_x = '1' WHERE rank_name LIKE ?", NORMALIZED));
    }

    @Test
    void theWarningIsBoundedAndNamesTheTableOnlyWhereATableGoes() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/database/compat/LegacyPermissionsSqlTranslator.java"));

        int translate = source.indexOf("String translated = this.translateNormalized(sql, context);");
        assertTrue(translate > 0 && translate < source.indexOf("this.warnUntranslated(sql);"));
        assertTrue(source.contains("this.warnedStatements.size() >= MAX_WARNED_STATEMENTS"));
        assertTrue(source.contains("(?:from|join|into|update|table)"));
    }
}
