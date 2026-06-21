package com.eu.habbo.messages.outgoing.inventory.nickicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserCustomizationData;
import com.eu.habbo.habbohotel.users.UserNickIcon;
import com.eu.habbo.habbohotel.users.UserPrefix;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserNickIconsComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserNickIconsComposer.class);

    private final Habbo habbo;

    public UserNickIconsComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserNickIconsComposer);

        if (this.habbo == null || this.habbo.getInventory() == null || this.habbo.getInventory().getNickIconsComponent() == null) {
            this.response.appendInt(0);
            return this.response;
        }

        Map<String, UserNickIcon> ownedByKey = new HashMap<>();
        List<UserNickIcon> ownedNickIcons = this.habbo.getInventory().getNickIconsComponent().getNickIcons();

        for (UserNickIcon nickIcon : ownedNickIcons) {
            ownedByKey.put(nickIcon.getIconKey().toLowerCase(), nickIcon);
        }

        Map<Integer, UserPrefix> ownedPrefixesByCatalogId = new HashMap<>();
        List<UserPrefix> ownedPrefixes = this.habbo.getInventory().getPrefixesComponent().getPrefixes();

        for (UserPrefix prefix : ownedPrefixes) {
            if (prefix.getCatalogPrefixId() > 0) {
                ownedPrefixesByCatalogId.put(prefix.getCatalogPrefixId(), prefix);
            }
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT icon_key, display_name, points, points_type FROM custom_nick_icons_catalog WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                List<CatalogNickIcon> catalogNickIcons = new ArrayList<>();

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

        UserCustomizationData customizationData = UserCustomizationData.fromHabbo(this.habbo);
        this.response.appendString(customizationData.displayOrder());
        this.response.appendInt(this.getSettingInt("max_length", 15));
        this.response.appendInt(this.getSettingInt("price_credits", 5));
        this.response.appendInt(this.getSettingInt("price_points", 0));
        this.response.appendInt(this.getSettingInt("points_type", 0));
        this.response.appendInt(this.getSettingInt("font_price_credits", 10));
        this.response.appendInt(this.getSettingInt("font_price_points", 0));
        this.response.appendInt(this.getSettingInt("font_points_type", 0));

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, display_name, text, color, icon, effect, font, points, points_type FROM custom_prefixes_catalog WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                List<CatalogPrefix> catalogPrefixes = new ArrayList<>();

                while (set.next()) {
                    catalogPrefixes.add(new CatalogPrefix(
                        set.getInt("id"),
                        set.getString("display_name"),
                        set.getString("text"),
                        set.getString("color"),
                        set.getString("icon"),
                        set.getString("effect"),
                        set.getString("font"),
                        set.getInt("points"),
                        set.getInt("points_type")));
                }

                this.response.appendInt(catalogPrefixes.size());

                for (CatalogPrefix catalogPrefix : catalogPrefixes) {
                    UserPrefix ownedPrefix = ownedPrefixesByCatalogId.get(catalogPrefix.id);

                    this.response.appendInt(catalogPrefix.id);
                    this.response.appendString(catalogPrefix.displayName != null ? catalogPrefix.displayName : catalogPrefix.text);
                    this.response.appendString(catalogPrefix.text != null ? catalogPrefix.text : "");
                    this.response.appendString(catalogPrefix.color != null ? catalogPrefix.color : "");
                    this.response.appendString(catalogPrefix.icon != null ? catalogPrefix.icon : "");
                    this.response.appendString(catalogPrefix.effect != null ? catalogPrefix.effect : "");
                    this.response.appendString(catalogPrefix.font != null ? catalogPrefix.font : "");
                    this.response.appendInt(catalogPrefix.points);
                    this.response.appendInt(catalogPrefix.pointsType);
                    this.response.appendInt(ownedPrefix != null ? 1 : 0);
                    this.response.appendInt((ownedPrefix != null && ownedPrefix.isActive()) ? 1 : 0);
                    this.response.appendInt(ownedPrefix != null ? ownedPrefix.getId() : 0);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception loading prefix catalog", e);
            this.response.appendInt(0);
        }

        this.response.appendInt(ownedPrefixes.size());

        for (UserPrefix prefix : ownedPrefixes) {
            this.response.appendInt(prefix.getId());
            this.response.appendString(prefix.getDisplayName() != null ? prefix.getDisplayName() : prefix.getText());
            this.response.appendString(prefix.getText());
            this.response.appendString(prefix.getColor());
            this.response.appendString(prefix.getIcon());
            this.response.appendString(prefix.getEffect());
            this.response.appendString(prefix.getFont());
            this.response.appendInt(prefix.isActive() ? 1 : 0);
            this.response.appendInt(prefix.isCustom() ? 1 : 0);
            this.response.appendInt(prefix.getPoints());
            this.response.appendInt(prefix.getPointsType());
            this.response.appendInt(prefix.getCatalogPrefixId());
        }

        return this.response;
    }

    private int getSettingInt(String key, int defaultValue) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT `value` FROM custom_prefix_settings WHERE key_name = ? LIMIT 1")) {
            statement.setString(1, key);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return Integer.parseInt(set.getString("value"));
                }
            }
        } catch (SQLException | NumberFormatException e) {
            LOGGER.error("Caught exception while resolving prefix setting {}", key, e);
        }

        return defaultValue;
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

    private static class CatalogPrefix {
        private final int id;
        private final String displayName;
        private final String text;
        private final String color;
        private final String icon;
        private final String effect;
        private final String font;
        private final int points;
        private final int pointsType;

        private CatalogPrefix(int id, String displayName, String text, String color, String icon, String effect, String font, int points, int pointsType) {
            this.id = id;
            this.displayName = displayName;
            this.text = text;
            this.color = color;
            this.icon = icon;
            this.effect = effect;
            this.font = font;
            this.points = points;
            this.pointsType = pointsType;
        }
    }
}
