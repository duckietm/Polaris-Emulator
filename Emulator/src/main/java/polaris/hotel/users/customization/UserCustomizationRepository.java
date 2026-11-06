package polaris.hotel.users.customization;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserCustomizationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserCustomizationRepository.class);
    private final DataSource dataSource;

    public UserCustomizationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Integer> loadPrefixSettings() {
        Map<String, Integer> settings = new HashMap<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT key_name, `value` FROM custom_prefix_settings");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                try {
                    settings.put(set.getString("key_name"), Integer.parseInt(set.getString("value")));
                } catch (NumberFormatException ignored) {
                    // Preserve legacy behavior: malformed optional settings use
                    // their caller-provided defaults.
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Error reading prefix settings", exception);
        }
        return Map.copyOf(settings);
    }

    public Optional<CatalogPrefixOffer> findCatalogPrefix(int id) throws SQLException {
        String sql = "SELECT display_name, text, color, icon, effect, font, points, points_type "
                + "FROM custom_prefixes_catalog WHERE id = ? AND enabled = 1 LIMIT 1";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CatalogPrefixOffer(
                        id,
                        set.getString("display_name"),
                        set.getString("text"),
                        set.getString("color"),
                        set.getString("icon"),
                        set.getString("effect"),
                        set.getString("font"),
                        set.getInt("points"),
                        set.getInt("points_type")));
            }
        }
    }

    public Optional<NickIconOffer> findNickIcon(String iconKey) throws SQLException {
        String sql =
                "SELECT points, points_type, enabled " + "FROM custom_nick_icons_catalog WHERE icon_key = ? LIMIT 1";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, iconKey);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return Optional.empty();
                }
                return Optional.of(new NickIconOffer(
                        iconKey, set.getInt("points"), set.getInt("points_type"), set.getBoolean("enabled")));
            }
        }
    }

    public record CatalogPrefixOffer(
            int id,
            String displayName,
            String text,
            String color,
            String icon,
            String effect,
            String font,
            int points,
            int pointsType) {}

    public record NickIconOffer(String iconKey, int points, int pointsType, boolean enabled) {}
}
