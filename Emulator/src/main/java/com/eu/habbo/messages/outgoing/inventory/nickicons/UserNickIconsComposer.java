package com.eu.habbo.messages.outgoing.inventory.nickicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserNickIcon;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserNickIconsComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserNickIconsComposer.class);

    private final Habbo habbo;

    public UserNickIconsComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserNickIconsComposer);

        if (this.habbo == null
                || this.habbo.getInventory() == null
                || this.habbo.getInventory().getNickIconsComponent() == null) {
            this.response.appendInt(0);
            return this.response;
        }

        Map<String, UserNickIcon> ownedByKey = new HashMap<>();
        List<UserNickIcon> ownedNickIcons =
                this.habbo.getInventory().getNickIconsComponent().getNickIcons();

        for (UserNickIcon nickIcon : ownedNickIcons) {
            ownedByKey.put(nickIcon.getIconKey().toLowerCase(), nickIcon);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT icon_key, display_name, points, points_type FROM custom_nick_icons_catalog WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                ArrayList<CatalogNickIcon> catalogNickIcons = new ArrayList<>();

                while (set.next()) {
                    catalogNickIcons.add(new CatalogNickIcon(
                            set.getString("icon_key"),
                            set.getString("display_name"),
                            set.getInt("points"),
                            set.getInt("points_type")));
                }

                this.response.appendInt(catalogNickIcons.size());

                for (CatalogNickIcon catalogNickIcon : catalogNickIcons) {
                    UserNickIcon ownedNickIcon = ownedByKey.get(catalogNickIcon.iconKey.toLowerCase());

                    this.response.appendString(catalogNickIcon.iconKey);
                    this.response.appendString(catalogNickIcon.displayName != null ? catalogNickIcon.displayName : "");
                    this.response.appendInt(catalogNickIcon.points);
                    this.response.appendInt(catalogNickIcon.pointsType);
                    this.response.appendInt(ownedNickIcon != null ? 1 : 0);
                    this.response.appendInt((ownedNickIcon != null && ownedNickIcon.isActive()) ? 1 : 0);
                    this.response.appendInt(ownedNickIcon != null ? ownedNickIcon.getId() : 0);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            this.response.appendInt(0);
        }

        return this.response;
    }

    private static class CatalogNickIcon {
        private final String iconKey;
        private final String displayName;
        private final int points;
        private final int pointsType;

        private CatalogNickIcon(String iconKey, String displayName, int points, int pointsType) {
            this.iconKey = iconKey;
            this.displayName = displayName;
            this.points = points;
            this.pointsType = pointsType;
        }
    }
}
