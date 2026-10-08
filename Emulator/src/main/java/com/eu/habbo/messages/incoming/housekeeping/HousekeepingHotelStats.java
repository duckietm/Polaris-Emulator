package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.database.SqlQueries;
import java.util.ArrayList;
import java.util.List;

/**
 * The series behind the dashboard charts, read from tables the hotel already fills: users who
 * entered a room, per hour of the last day (room_enter_log), and bans issued, per day of the last
 * two weeks (bans). The hotel keeps no history of the online count itself, so room activity is
 * the closest hourly measure there is. Buckets are unix seconds at the start of the hour or UTC
 * day; empty buckets are left out and filled by the client.
 */
final class HousekeepingHotelStats {
    static final String SERIES_ACTIVITY = "activity";
    static final String SERIES_BANS = "bans";

    static final int ACTIVITY_HOURS = 24;
    static final int BAN_DAYS = 14;

    static final String ACTIVITY_SQL =
            "SELECT FLOOR(`timestamp` / 3600) * 3600 AS bucket, COUNT(DISTINCT user_id) AS value "
                    + "FROM room_enter_log WHERE `timestamp` >= ? GROUP BY bucket ORDER BY bucket";
    static final String BANS_SQL = "SELECT FLOOR(`timestamp` / 86400) * 86400 AS bucket, COUNT(*) AS value "
            + "FROM bans WHERE `timestamp` >= ? GROUP BY bucket ORDER BY bucket";

    private HousekeepingHotelStats() {}

    /** Rows of {series, bucket, value}; a series whose query fails is simply missing. */
    static List<List<String>> rows(int now) {
        List<List<String>> rows = new ArrayList<>();

        int activityFrom = (now / 3600 - (ACTIVITY_HOURS - 1)) * 3600;
        int bansFrom = (now / 86400 - (BAN_DAYS - 1)) * 86400;

        rows.addAll(series(SERIES_ACTIVITY, ACTIVITY_SQL, activityFrom));
        rows.addAll(series(SERIES_BANS, BANS_SQL, bansFrom));

        return rows;
    }

    private static List<List<String>> series(String name, String sql, int from) {
        try {
            return SqlQueries.query(
                    sql,
                    set -> List.of(name, String.valueOf(set.getLong("bucket")), String.valueOf(set.getLong("value"))),
                    from);
        } catch (SqlQueries.DataAccessException e) {
            return List.of();
        }
    }
}
