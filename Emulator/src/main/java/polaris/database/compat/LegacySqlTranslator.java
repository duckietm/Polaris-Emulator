package polaris.database.compat;

import java.sql.SQLException;

/**
 * Translates a single SQL statement written against a pre-Polaris (Arcturus
 * Morningstar) schema into its Polaris equivalent.
 *
 * Translators are consulted in registration order by {@link LegacySqlBridge};
 * the first translator that returns a non-null statement wins.
 */
public interface LegacySqlTranslator {

    /**
     * Cheap pre-filter, called with the lower-cased statement. Only when this
     * returns true is {@link #translate(String, TranslationContext)} invoked,
     * keeping the bridge nearly free for the emulator's own (modern) queries.
     */
    boolean appliesTo(String lowerCaseSql);

    /**
     * @return the rewritten statement, or null when this translator does not
     * apply and the statement should be handed to the next translator (or
     * passed through unchanged).
     */
    String translate(String sql, TranslationContext context) throws SQLException;
}
