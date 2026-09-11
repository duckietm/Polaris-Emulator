package com.eu.habbo.habbohotel.modtool;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the report rows behind the official "my_reports" window. Closed
 * tickets are dropped from {@link ModToolManager}'s in-memory map, so the window is
 * served straight from {@code support_tickets}.
 */
public final class MyReportStatusRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyReportStatusRepository.class);

    /** How many reports the window lists; the official table is not paged. */
    public static final int REPORT_LIMIT = 50;

    private static final String SELECT_SQL = """
            SELECT support_tickets.id, support_tickets.timestamp, support_tickets.issue,
                   support_tickets.category, support_tickets.state, support_tickets.closed_timestamp,
                   support_tickets.sanctioned, support_tickets.sanction_auto,
                   support_tickets.appeal_state, support_tickets.appeal_timestamp,
                   support_tickets.appeal_resolved_timestamp,
                   reported.username AS reported_username
            FROM support_tickets
            LEFT JOIN users AS reported ON reported.id = support_tickets.reported_id
            WHERE support_tickets.sender_id = ?
            ORDER BY support_tickets.timestamp DESC
            LIMIT ?
            """;

    private MyReportStatusRepository() {}

    public static List<MyReportStatus> findByReporter(int userId) {
        List<MyReportStatus> reports = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setInt(1, userId);
            statement.setInt(2, REPORT_LIMIT);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    reports.add(read(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return reports;
    }

    public static MyReportStatus findByReporterAndId(int userId, int reportId) {
        for (MyReportStatus report : findByReporter(userId)) {
            if (report.id() == reportId) {
                return report;
            }
        }

        return null;
    }

    /**
     * Files the appeal. Returns false when the row moved on in the meantime, so a
     * duplicate click cannot appeal twice.
     */
    public static boolean appeal(int userId, int reportId, int timestamp) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE support_tickets SET appeal_state = ?, appeal_timestamp = ?, appeal_resolved_timestamp = 0 "
                                + "WHERE id = ? AND sender_id = ? AND appeal_state = ? AND state = 0 AND sanctioned = 0")) {
            statement.setInt(1, MyReportStatus.APPEAL_PENDING);
            statement.setInt(2, timestamp);
            statement.setInt(3, reportId);
            statement.setInt(4, userId);
            statement.setInt(5, MyReportStatus.APPEAL_NONE);

            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }

    private static MyReportStatus read(ResultSet set) throws SQLException {
        int closedTimestamp = set.getInt("closed_timestamp");
        boolean closed = set.getInt("state") == ModToolTicketState.CLOSED.getState();
        int appealResolved = set.getInt("appeal_resolved_timestamp");
        String reportedUsername = set.getString("reported_username");

        return new MyReportStatus(
                set.getInt("id"),
                toMillis(set.getInt("timestamp")),
                set.getString("issue") == null ? "" : set.getString("issue"),
                set.getInt("category"),
                reportedUsername == null ? "" : reportedUsername,
                closed && closedTimestamp > 0 ? toMillis(closedTimestamp) : -1L,
                set.getInt("sanctioned") == 1,
                set.getInt("sanction_auto") == 1,
                set.getInt("appeal_state"),
                toMillis(set.getInt("appeal_timestamp")),
                appealResolved > 0 ? toMillis(appealResolved) : -1L);
    }

    /** The official reader wants epoch milliseconds; the emulator stores unix seconds. */
    private static long toMillis(int seconds) {
        return seconds <= 0 ? 0L : seconds * 1000L;
    }
}
