package polaris.hotel.items.rentable;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * Pure arithmetic of rented furniture: an item either has no rent period
 * ({@link #NEVER}) or a unix expiry timestamp, and the client only ever sees
 * the seconds left.
 */
public final class RentableFurniture {
    /** Value of {@code items.expires} for furni owned outright. */
    public static final int NEVER = -1;

    public static final int SECONDS_PER_DAY = 86400;

    private RentableFurniture() {}

    /** Seconds still left on the rent period, {@link #NEVER} when there is none. */
    public static int secondsToExpiration(int expiresTimestamp, int now) {
        if (expiresTimestamp <= 0) {
            return NEVER;
        }
        return Math.max(0, expiresTimestamp - now);
    }

    /** Expiry of a fresh rental of {@code rentDays} started at {@code now}. */
    public static int expiresAfter(int now, int rentDays) {
        return now + Math.max(0, rentDays) * SECONDS_PER_DAY;
    }

    /**
     * Expiry after extending an existing rental: the new days are added to
     * whatever is left, an already expired rental restarts from now.
     */
    public static int extend(int expiresTimestamp, int now, int rentDays) {
        int base = expiresTimestamp > now ? expiresTimestamp : now;
        return base + Math.max(0, rentDays) * SECONDS_PER_DAY;
    }

    public static boolean hasRentPeriod(int expiresTimestamp) {
        return expiresTimestamp > 0;
    }

    /** {@code items.expires}, tolerant of result sets that predate the column. */
    public static int readExpires(ResultSet set) throws SQLException {
        return hasColumn(set, "expires") ? set.getInt("expires") : NEVER;
    }

    /** {@code catalog_items.rent_days}, 0 for result sets without the column (builders club offers). */
    public static int readRentDays(ResultSet set) throws SQLException {
        return hasColumn(set, "rent_days") ? set.getInt("rent_days") : 0;
    }

    private static boolean hasColumn(ResultSet set, String column) throws SQLException {
        ResultSetMetaData metaData = set.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(metaData.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }
}
