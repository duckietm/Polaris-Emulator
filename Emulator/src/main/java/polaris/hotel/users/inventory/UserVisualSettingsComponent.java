package polaris.hotel.users.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;

public class UserVisualSettingsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserVisualSettingsComponent.class);
    public static final String DEFAULT_DISPLAY_ORDER = "icon-prefix-name";
    private static final Set<String> ALLOWED_PARTS = new HashSet<>(Arrays.asList("icon", "prefix", "name"));

    private final User user;
    private String displayOrder = DEFAULT_DISPLAY_ORDER;

    public UserVisualSettingsComponent(User user) {
        this.user = user;
        this.loadSettings();
    }

    private void loadSettings() {
        this.displayOrder = loadDisplayOrder(this.user.getUserInfo().getId());
    }

    public String getDisplayOrder() {
        return sanitizeDisplayOrder(this.displayOrder);
    }

    public void setDisplayOrder(String displayOrder) {
        this.displayOrder = sanitizeDisplayOrder(displayOrder);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO user_visual_settings (user_id, display_order) VALUES (?, ?) ON DUPLICATE KEY UPDATE display_order = VALUES(display_order)")) {
            statement.setInt(1, this.user.getUserInfo().getId());
            statement.setString(2, this.displayOrder);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while saving user visual settings", e);
        }
    }

    public static String loadDisplayOrder(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT display_order FROM user_visual_settings WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return sanitizeDisplayOrder(set.getString("display_order"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while loading user visual settings", e);
        }

        return DEFAULT_DISPLAY_ORDER;
    }

    public static String sanitizeDisplayOrder(String displayOrder) {
        if (displayOrder == null || displayOrder.trim().isEmpty()) {
            return DEFAULT_DISPLAY_ORDER;
        }

        String[] parts = displayOrder.trim().toLowerCase().split("-");

        if (parts.length != 3) {
            return DEFAULT_DISPLAY_ORDER;
        }

        Set<String> uniqueParts = new HashSet<>();

        for (String part : parts) {
            if (!ALLOWED_PARTS.contains(part) || !uniqueParts.add(part)) {
                return DEFAULT_DISPLAY_ORDER;
            }
        }

        return String.join("-", parts);
    }

    public void dispose() {
        this.displayOrder = DEFAULT_DISPLAY_ORDER;
    }
}
