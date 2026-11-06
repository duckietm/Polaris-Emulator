package polaris.hotel.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;

public class UserPrefix implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPrefix.class);

    private int id;
    private final int userId;
    private String text;
    private String color;
    private String icon;
    private String effect;
    private String font;
    private int catalogPrefixId;
    private String displayName;
    private int points;
    private int pointsType;
    private boolean custom;
    private boolean active;
    private boolean needsInsert;
    private boolean needsUpdate;
    private boolean needsDelete;

    public UserPrefix(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.userId = set.getInt("user_id");
        this.text = set.getString("text");
        this.color = set.getString("color");
        this.icon = set.getString("icon");
        if (this.icon == null) this.icon = "";
        this.effect = set.getString("effect");
        if (this.effect == null) this.effect = "";
        this.font = readString(set, "font", "");
        this.catalogPrefixId = readInt(set, "catalog_prefix_id", 0);
        this.displayName = readString(set, "display_name", this.text);
        this.points = readInt(set, "points", 0);
        this.pointsType = readInt(set, "points_type", 0);
        this.custom = readBoolean(set, "is_custom", true);
        this.active = set.getBoolean("active");
        this.needsInsert = false;
        this.needsUpdate = false;
        this.needsDelete = false;
    }

    public UserPrefix(int userId, String text, String color, String icon, String effect) {
        this(userId, text, color, icon, effect, "", 0, text, 0, 0, true);
    }

    public UserPrefix(
            int userId,
            String text,
            String color,
            String icon,
            String effect,
            String font,
            int catalogPrefixId,
            String displayName,
            int points,
            int pointsType,
            boolean custom) {
        this.id = 0;
        this.userId = userId;
        this.text = text;
        this.color = color;
        this.icon = icon != null ? icon : "";
        this.effect = effect != null ? effect : "";
        this.font = font != null ? font : "";
        this.catalogPrefixId = catalogPrefixId;
        this.displayName = (displayName != null && !displayName.isEmpty()) ? displayName : text;
        this.points = points;
        this.pointsType = pointsType;
        this.custom = custom;
        this.active = false;
        this.needsInsert = true;
        this.needsUpdate = false;
        this.needsDelete = false;
    }

    @Override
    public void run() {
        try {
            if (this.needsInsert) {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO user_prefixes (user_id, text, color, icon, effect, font, active, catalog_prefix_id, display_name, points, points_type, is_custom) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, this.userId);
                    statement.setString(2, this.text);
                    statement.setString(3, this.color);
                    statement.setString(4, this.icon);
                    statement.setString(5, this.effect);
                    statement.setString(6, this.font);
                    statement.setBoolean(7, this.active);
                    statement.setInt(8, this.catalogPrefixId);
                    statement.setString(9, this.displayName);
                    statement.setInt(10, this.points);
                    statement.setInt(11, this.pointsType);
                    statement.setBoolean(12, this.custom);
                    statement.execute();
                    try (ResultSet set = statement.getGeneratedKeys()) {
                        if (set.next()) {
                            this.id = set.getInt(1);
                        }
                    }
                }
                this.needsInsert = false;
            } else if (this.needsDelete) {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement =
                                connection.prepareStatement("DELETE FROM user_prefixes WHERE id = ? AND user_id = ?")) {
                    statement.setInt(1, this.id);
                    statement.setInt(2, this.userId);
                    statement.execute();
                }
                this.needsDelete = false;
            } else if (this.needsUpdate) {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "UPDATE user_prefixes SET text = ?, color = ?, icon = ?, effect = ?, font = ?, active = ?, catalog_prefix_id = ?, display_name = ?, points = ?, points_type = ?, is_custom = ? WHERE id = ? AND user_id = ?")) {
                    statement.setString(1, this.text);
                    statement.setString(2, this.color);
                    statement.setString(3, this.icon);
                    statement.setString(4, this.effect);
                    statement.setString(5, this.font);
                    statement.setBoolean(6, this.active);
                    statement.setInt(7, this.catalogPrefixId);
                    statement.setString(8, this.displayName);
                    statement.setInt(9, this.points);
                    statement.setInt(10, this.pointsType);
                    statement.setBoolean(11, this.custom);
                    statement.setInt(12, this.id);
                    statement.setInt(13, this.userId);
                    statement.execute();
                }
                this.needsUpdate = false;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int getId() {
        return this.id;
    }

    public int getUserId() {
        return this.userId;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getColor() {
        return this.color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String icon) {
        this.icon = icon != null ? icon : "";
    }

    public String getEffect() {
        return this.effect;
    }

    public void setEffect(String effect) {
        this.effect = effect != null ? effect : "";
    }

    public String getFont() {
        return this.font;
    }

    public void setFont(String font) {
        this.font = font != null ? font : "";
    }

    public int getCatalogPrefixId() {
        return this.catalogPrefixId;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getPoints() {
        return this.points;
    }

    public int getPointsType() {
        return this.pointsType;
    }

    public boolean isCustom() {
        return this.custom;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.needsUpdate = true;
    }

    public void needsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
    }

    public void needsInsert(boolean needsInsert) {
        this.needsInsert = needsInsert;
    }

    public void needsDelete(boolean needsDelete) {
        this.needsDelete = needsDelete;
    }

    private static int readInt(ResultSet set, String columnName, int defaultValue) {
        try {
            return set.getInt(columnName);
        } catch (SQLException e) {
            return defaultValue;
        }
    }

    private static String readString(ResultSet set, String columnName, String defaultValue) {
        try {
            String value = set.getString(columnName);
            return value != null ? value : defaultValue;
        } catch (SQLException e) {
            return defaultValue;
        }
    }

    private static boolean readBoolean(ResultSet set, String columnName, boolean defaultValue) {
        try {
            return set.getBoolean(columnName);
        } catch (SQLException e) {
            return defaultValue;
        }
    }
}
