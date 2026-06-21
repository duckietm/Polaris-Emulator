package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.inventory.UserVisualSettingsComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public record UserCustomizationData(String nickIcon, String displayOrder, String prefixText, String prefixColor, String prefixIcon, String prefixEffect, String prefixFont) {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserCustomizationData.class);

    public UserCustomizationData {
        nickIcon = nickIcon != null ? nickIcon : "";
        displayOrder = UserVisualSettingsComponent.sanitizeDisplayOrder(displayOrder);
        prefixText = prefixText != null ? prefixText : "";
        prefixColor = prefixColor != null ? prefixColor : "";
        prefixIcon = prefixIcon != null ? prefixIcon : "";
        prefixEffect = prefixEffect != null ? prefixEffect : "";
        prefixFont = prefixFont != null ? prefixFont : "";
    }

    public static UserCustomizationData fromHabbo(Habbo habbo) {
        if (habbo == null) {
            return empty();
        }

        String nickIcon = "";
        String displayOrder = UserVisualSettingsComponent.DEFAULT_DISPLAY_ORDER;
        String prefixText = "";
        String prefixColor = "";
        String prefixIcon = "";
        String prefixEffect = "";
        String prefixFont = "";

        if (habbo.getInventory() != null) {
            if (habbo.getInventory().getNickIconsComponent() != null) {
                UserNickIcon activeNickIcon = habbo.getInventory().getNickIconsComponent().getActiveNickIcon();

                if (activeNickIcon != null && activeNickIcon.getIconKey() != null) {
                    nickIcon = activeNickIcon.getIconKey();
                }
            }

            if (habbo.getInventory().getPrefixesComponent() != null) {
                UserPrefix activePrefix = habbo.getInventory().getPrefixesComponent().getActivePrefix();

                if (activePrefix != null) {
                    prefixText = activePrefix.getText();
                    prefixColor = activePrefix.getColor();
                    prefixIcon = activePrefix.getIcon();
                    prefixEffect = activePrefix.getEffect();
                    prefixFont = activePrefix.getFont();
                }
            }

            if (habbo.getInventory().getUserVisualSettingsComponent() != null) {
                displayOrder = habbo.getInventory().getUserVisualSettingsComponent().getDisplayOrder();
            }
        }

        return new UserCustomizationData(nickIcon, displayOrder, prefixText, prefixColor, prefixIcon, prefixEffect, prefixFont);
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

        return new UserCustomizationData(nickIcon, displayOrder, prefixText, prefixColor, prefixIcon, prefixEffect, prefixFont);
    }

    public static UserCustomizationData empty() {
        return new UserCustomizationData("", UserVisualSettingsComponent.DEFAULT_DISPLAY_ORDER, "", "", "", "", "");
    }
}
