package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseLogger.class);

    private final ConcurrentLinkedQueue<DatabaseLoggable> loggables = new ConcurrentLinkedQueue<>();

    public void store(DatabaseLoggable loggable) {
        this.loggables.add(loggable);
    }

    public void save() {
        if (Emulator.getDatabase() == null || Emulator.getDatabase().getDataSource() == null) {
            return;
        }

        // Drain the queue into a local snapshot so new loggables that arrive
        // during this save cycle roll into the next flush instead of extending
        // the current one indefinitely.
        List<DatabaseLoggable> snapshot = new ArrayList<>();
        DatabaseLoggable next;
        while ((next = this.loggables.poll()) != null) {
            snapshot.add(next);
        }

        if (snapshot.isEmpty()) {
            return;
        }

        // Group by SQL query so each distinct statement only prepares and
        // executeBatches once. LinkedHashMap preserves first-seen order so
        // auto-increment ids on chat/log tables correlate with the time the
        // events actually happened.
        Map<String, List<DatabaseLoggable>> byQuery = new LinkedHashMap<>();
        for (DatabaseLoggable loggable : snapshot) {
            byQuery.computeIfAbsent(loggable.getQuery(), k -> new ArrayList<>()).add(loggable);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            this.writeGroups(connection, byQuery);
        } catch (SQLException e) {
            // The batch was already taken off the queue: without putting it back, a
            // database hiccup threw away everything this cycle had drained.
            this.loggables.addAll(snapshot);
            LOGGER.error(
                    "Exception caught while saving loggables to database; {} kept for the next flush.",
                    snapshot.size(),
                    e);
        }
    }

    private void writeGroups(Connection connection, Map<String, List<DatabaseLoggable>> byQuery) {
        for (Map.Entry<String, List<DatabaseLoggable>> group : byQuery.entrySet()) {
            List<DatabaseLoggable> entries = group.getValue();
            try (PreparedStatement statement = connection.prepareStatement(group.getKey())) {
                for (DatabaseLoggable loggable : entries) {
                    loggable.log(statement);
                }
                statement.executeBatch();
            } catch (SQLException e) {
                // One bad row (a text longer than its column, say) used to throw the whole group away, chat
                // lines included: the group is written again one row at a time and only the bad rows are dropped.
                LOGGER.error(
                        "Exception caught while saving loggable group of size {}, writing it row by row: {}",
                        entries.size(),
                        group.getKey(),
                        e);
                int dropped = writeOneByOne(connection, group.getKey(), entries);
                if (dropped > 0) {
                    LOGGER.error("{} loggable row(s) could not be saved and were dropped: {}", dropped, group.getKey());
                }
            }
        }
    }

    /** Writes each row on its own and answers how many failed. */
    static int writeOneByOne(Connection connection, String query, List<DatabaseLoggable> entries) {
        int dropped = 0;
        for (DatabaseLoggable loggable : entries) {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                loggable.log(statement);
                statement.executeBatch();
            } catch (SQLException e) {
                dropped++;
            }
        }
        return dropped;
    }
}
