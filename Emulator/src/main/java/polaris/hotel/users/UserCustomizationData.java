package polaris.hotel.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.inventory.UserVisualSettingsComponent;

public class UserCustomizationData {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserCustomizationData.class);

    public final String nickIcon;
    public final String displayOrder;
    public final String prefixText;
    public final String prefixColor;
    public final String prefixIcon;
    public final String prefixEffect;
    public final String prefixFont;

    private UserCustomizationData(
            String nickIcon,
            String displayOrder,
            String prefixText,
            String prefixColor,
            String prefixIcon,
            String prefixEffect,
            String prefixFont) {
        this.nickIcon = nickIcon != null ? nickIcon : "";
        this.displayOrder = UserVisualSettingsComponent.sanitizeDisplayOrder(displayOrder);
        this.prefixText = prefixText != null ? prefixText : "";
        this.prefixColor = prefixColor != null ? prefixColor : "";
        this.prefixIcon = prefixIcon != null ? prefixIcon : "";
        this.prefixEffect = prefixEffect != null ? prefixEffect : "";
        this.prefixFont = prefixFont != null ? prefixFont : "";
    }

    public static UserCustomizationData fromUser(User user) {
        if (user == null) {
            return empty();
        }

        String nickIcon = "";
        String displayOrder = UserVisualSettingsComponent.DEFAULT_DISPLAY_ORDER;
        String prefixText = "";
        String prefixColor = "";
        String prefixIcon = "";
        String prefixEffect = "";
        String prefixFont = "";

        if (user.getInventory() != null) {
            if (user.getInventory().getNickIconsComponent() != null) {
                UserNickIcon activeNickIcon =
                        user.getInventory().getNickIconsComponent().getActiveNickIcon();

                if (activeNickIcon != null && activeNickIcon.getIconKey() != null) {
                    nickIcon = activeNickIcon.getIconKey();
                }
            }

            if (user.getInventory().getPrefixesComponent() != null) {
                UserPrefix activePrefix =
                        user.getInventory().getPrefixesComponent().getActivePrefix();

                if (activePrefix != null) {
                    prefixText = activePrefix.getText();
                    prefixColor = activePrefix.getColor();
                    prefixIcon = activePrefix.getIcon();
                    prefixEffect = activePrefix.getEffect();
                    prefixFont = activePrefix.getFont();
                }
            }

            if (user.getInventory().getUserVisualSettingsComponent() != null) {
                displayOrder =
                        user.getInventory().getUserVisualSettingsComponent().getDisplayOrder();
            }
        }

        return new UserCustomizationData(
                nickIcon, displayOrder, prefixText, prefixColor, prefixIcon, prefixEffect, prefixFont);
    }

    public static UserCustomizationData fromUserId(int userId) {
        String nickIcon = "";
        String prefixText = "";
        String prefixColor = "";
        String prefixIcon = "";
        String prefixEffect = "";
        String prefixFont = "";
        String displayOrder = UserVisualSettingsComponent.loadDisplayOrder(userId);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement nickStatement = connection.prepareStatement(
                    "SELECT icon_key FROM user_nick_icons WHERE user_id = ? AND active = 1 LIMIT 1")) {
                nickStatement.setInt(1, userId);

                try (ResultSet set = nickStatement.executeQuery()) {
                    if (set.next()) {
                        nickIcon = set.getString("icon_key");
                    }
                }
            }

            try (PreparedStatement prefixStatement = connection.prepareStatement(
                    "SELECT text, color, icon, effect, font FROM user_prefixes WHERE user_id = ? AND active = 1 LIMIT 1")) {
                prefixStatement.setInt(1, userId);

                try (ResultSet set = prefixStatement.executeQuery()) {
                    if (set.next()) {
                        prefixText = set.getString("text");
                        prefixColor = set.getString("color");
                        prefixIcon = set.getString("icon");
                        prefixEffect = set.getString("effect");
                        prefixFont = set.getString("font");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while loading user customization data", e);
        }

        return new UserCustomizationData(
                nickIcon, displayOrder, prefixText, prefixColor, prefixIcon, prefixEffect, prefixFont);
    }

    public static UserCustomizationData empty() {
        return new UserCustomizationData("", UserVisualSettingsComponent.DEFAULT_DISPLAY_ORDER, "", "", "", "", "");
    }
}
