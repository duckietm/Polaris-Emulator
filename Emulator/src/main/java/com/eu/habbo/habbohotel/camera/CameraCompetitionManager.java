package com.eu.habbo.habbohotel.camera;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The photo competition the camera checkout submits to. A player may submit a few times a day and
 * only their newest submission counts, which is what the official texts promise: nothing is replaced
 * here, the rows are the history and the newest row of a player is the entry that stands.
 *
 * <p>A single final instance keeps the class free of mutable statics, like the other managers, and
 * the handler stays free of database work.
 */
public class CameraCompetitionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraCompetitionManager.class);

    private static final int DAY_SECONDS = 86400;

    private static final CameraCompetitionManager INSTANCE = new CameraCompetitionManager();

    private CameraCompetitionManager() {}

    public static CameraCompetitionManager getInstance() {
        return INSTANCE;
    }

    /** How many photos one player may submit in a day. */
    public int dailyLimit() {
        return Math.max(1, Emulator.getConfig().getInt("camera.competition.daily.limit", 3));
    }

    /** How many they already submitted in the last day. */
    public int submissionsToday(int userId) {
        int since = Emulator.getIntUnixTimestamp() - DAY_SECONDS;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) AS used FROM camera_competition_entries WHERE user_id = ?"
                                + " AND submitted_at > ?")) {
            statement.setInt(1, userId);
            statement.setInt(2, since);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt("used") : 0;
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return Integer.MAX_VALUE;
        }
    }

    /** Writes the submission down. False when it could not be stored, so nothing is promised. */
    public boolean submit(int userId, int roomId, String url) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO camera_competition_entries (user_id, room_id, url, submitted_at)"
                                + " VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, userId);
            statement.setInt(2, roomId);
            statement.setString(3, url);
            statement.setInt(4, Emulator.getIntUnixTimestamp());
            statement.execute();
            return true;
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return false;
        }
    }
}
