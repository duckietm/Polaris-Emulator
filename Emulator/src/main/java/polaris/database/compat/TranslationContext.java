package polaris.database.compat;

import java.sql.SQLException;
import java.util.List;

/**
 * Runtime information a {@link LegacySqlTranslator} may need to rewrite a
 * legacy statement. Kept as an interface so translators can be unit-tested
 * without a live database connection.
 */
public interface TranslationContext {

    /**
     * Whether the database uses the normalized permissions schema
     * (permission_ranks + permission_definitions) instead of the legacy
     * single permissions table.
     */
    boolean isNormalizedPermissionsSchema() throws SQLException;

    /**
     * The ids of all ranks in permission_ranks, ascending. Only meaningful
     * when {@link #isNormalizedPermissionsSchema()} is true.
     */
    List<Integer> rankIds() throws SQLException;
}
