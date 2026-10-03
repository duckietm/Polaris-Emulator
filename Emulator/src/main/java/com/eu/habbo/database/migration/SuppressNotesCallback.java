package com.eu.habbo.database.migration;

import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;

/**
 * Additive migrations rely on IF [NOT] EXISTS, which MariaDB reports as a Note per skipped statement; Flyway would
 * log each Note as a warning. Real warnings (data truncation, ignored rows) are still reported.
 *
 * <p>MariaDB deprecates {@code sql_notes} in favour of {@code note_verbosity}; servers that predate it still need the
 * old variable.
 */
final class SuppressNotesCallback extends BaseCallback {
    static final String NOTE_VERBOSITY_SQL = "SET SESSION note_verbosity = ''";
    static final String LEGACY_SQL_NOTES_SQL = "SET SESSION sql_notes = 0";

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_CONNECT;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        try (Statement statement = context.getConnection().createStatement()) {
            try {
                statement.execute(NOTE_VERBOSITY_SQL);
            } catch (SQLException unsupported) {
                statement.execute(LEGACY_SQL_NOTES_SQL);
            }
        } catch (SQLException exception) {
            throw new FlywayException("Could not silence MariaDB notes for the migration session", exception);
        }
    }

    @Override
    public String getCallbackName() {
        return "suppress-mariadb-notes";
    }
}
