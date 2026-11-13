package polaris.hotel.users.firstlogin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.networking.gameserver.auth.RegistrationSupport;

/**
 * The first-login setup: an account that was not made on the client's own register screen (a CMS account) never chose
 * a look or a starter room there, so the client offers both the first time it logs in.
 *
 * <p>{@code users_first_login_setup} holds one row per account that went through either path: the register screen
 * writes it as completed, the first login of any other account writes it as pending when the account owns no room.
 * Accounts that existed before have no row and are never asked. The offer comes back at every login until the user
 * answers it.
 */
public final class FirstLoginSetupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirstLoginSetupService.class);

    /** Setting that turns the offer off for hotels whose CMS has its own. */
    public static final String ENABLED_KEY = "hotel.first_login.setup.enabled";

    /** The register screen's mark: this account chose its look and room there. */
    public static final String MARK_REGISTERED_SQL =
            "INSERT IGNORE INTO users_first_login_setup (user_id, completed) VALUES (?, 1)";

    /** The most starter rooms one offer carries. */
    public static final int MAX_TEMPLATES = 50;

    /** A starter room the user may take. */
    public record Template(int id, String title, String description, String thumbnail) {}

    /** Copies a template into a room of the user's own and gives the new room's id, or 0. */
    @FunctionalInterface
    public interface RoomCloner {
        int clone(Connection connection, int templateId, int userId, String userName) throws SQLException;
    }

    private final DataSource dataSource;
    private final RoomCloner cloner;

    public FirstLoginSetupService(DataSource dataSource) {
        this(dataSource, RegistrationSupport::cloneTemplateForUser);
    }

    public FirstLoginSetupService(DataSource dataSource, RoomCloner cloner) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.cloner = Objects.requireNonNull(cloner, "cloner");
    }

    /**
     * The first login of an account: it gets the offer when it has no room yet. An account the register screen made
     * already has its row, which is left as it is.
     */
    public void begin(int userId, int homeRoom) {
        try (Connection connection = this.dataSource.getConnection()) {
            boolean pending = homeRoom <= 0 && !ownsRoom(connection, userId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO users_first_login_setup (user_id, completed) VALUES (?, ?)")) {
                statement.setInt(1, userId);
                statement.setInt(2, pending ? 0 : 1);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("First-login setup could not be started for user {}", userId, e);
        }
    }

    /** Whether the user still has the offer to answer. */
    public boolean isPending(int userId) {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM users_first_login_setup WHERE user_id = ? AND completed = 0 LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("First-login setup could not be read for user {}", userId, e);
            return false;
        }
    }

    /** The starter rooms on offer, in the hotel's order. */
    public List<Template> templates() {
        List<Template> templates = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT template_id, title, description, thumbnail FROM room_templates WHERE enabled = '1' "
                                + "ORDER BY sort_order ASC, template_id ASC LIMIT " + MAX_TEMPLATES);
                ResultSet set = statement.executeQuery()) {
            while (templates.size() < MAX_TEMPLATES && set.next()) {
                templates.add(new Template(
                        set.getInt("template_id"),
                        text(set.getString("title")),
                        text(set.getString("description")),
                        text(set.getString("thumbnail"))));
            }
        } catch (SQLException e) {
            LOGGER.error("Starter rooms could not be read", e);
        }
        return templates;
    }

    /**
     * The user's answer: the offer ends, and with a template the user gets that room when they have none. Only the
     * first answer counts, so sending it twice gives one room.
     *
     * @param templateId the starter room, or 0 for none
     * @return the new room's id, or 0 when no room was made
     */
    public int complete(int userId, String userName, int templateId) {
        try (Connection connection = this.dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users_first_login_setup SET completed = 1 WHERE user_id = ? AND completed = 0")) {
                statement.setInt(1, userId);
                if (statement.executeUpdate() == 0) return 0;
            }

            if (templateId <= 0 || ownsRoom(connection, userId)) return 0;

            return Math.max(0, this.cloner.clone(connection, templateId, userId, userName));
        } catch (SQLException e) {
            LOGGER.error("First-login setup could not be completed for user {}", userId, e);
            return 0;
        }
    }

    private static boolean ownsRoom(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT 1 FROM rooms WHERE owner_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
