package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserCustomizationData {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserCustomizationData.class);

    public final String nickIcon;

    private UserCustomizationData(String nickIcon) {
        this.nickIcon = nickIcon != null ? nickIcon : "";
    }

    public static UserCustomizationData fromHabbo(Habbo habbo) {
        if (habbo == null) {
            return empty();
        }

        String nickIcon = "";

        if (habbo.getInventory() != null) {
            if (habbo.getInventory().getNickIconsComponent() != null) {
                UserNickIcon activeNickIcon =
                        habbo.getInventory().getNickIconsComponent().getActiveNickIcon();

                if (activeNickIcon != null && activeNickIcon.getIconKey() != null) {
                    nickIcon = activeNickIcon.getIconKey();
                }
            }
        }

        return new UserCustomizationData(nickIcon);
    }

    public static UserCustomizationData fromUserId(int userId) {
        String nickIcon = "";
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
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while loading user customization data", e);
        }

        return new UserCustomizationData(nickIcon);
    }

    public static UserCustomizationData empty() {
        return new UserCustomizationData("");
    }
}
