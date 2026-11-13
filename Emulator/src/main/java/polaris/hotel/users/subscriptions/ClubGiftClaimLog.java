package polaris.hotel.users.subscriptions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The record of chosen club gifts ({@code club_gift_claims}): who took which gift and when. How many gifts a member
 * may still take stays with {@code users_settings.hc_gifts_claimed}; this table is the history behind that number,
 * for staff and housekeeping tools. A row that cannot be written is logged and does not undo the gift.
 */
public final class ClubGiftClaimLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClubGiftClaimLog.class);

    /** The longest gift name the table keeps. */
    public static final int MAX_NAME_LENGTH = 100;

    static final String INSERT_SQL =
            "INSERT INTO club_gift_claims (user_id, catalog_item_id, catalog_name, claimed_at) VALUES (?, ?, ?, ?)";

    private ClubGiftClaimLog() {}

    /**
     * Writes one chosen gift.
     *
     * @param catalogItemId the gift's {@code catalog_items.id}
     * @param catalogName the gift's {@code catalog_items.catalog_name} as it was when chosen
     * @param claimedAt unix seconds
     * @return whether the row was written
     */
    public static boolean record(
            DataSource dataSource, int userId, int catalogItemId, String catalogName, int claimedAt) {
        String name = catalogName == null ? "" : catalogName;
        if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setInt(1, userId);
            statement.setInt(2, catalogItemId);
            statement.setString(3, name);
            statement.setInt(4, claimedAt);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error(
                    "Club gift claim of user {} (catalog item {}) could not be recorded", userId, catalogItemId, e);
            return false;
        }
    }
}
