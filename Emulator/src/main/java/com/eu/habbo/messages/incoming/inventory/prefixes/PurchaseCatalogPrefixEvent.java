package com.eu.habbo.messages.incoming.inventory.prefixes;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserPrefix;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PurchaseCatalogPrefixEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseCatalogPrefixEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int catalogPrefixId = this.packet.readInt();
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || catalogPrefixId <= 0) {
            return;
        }

        if (habbo.getInventory().getPrefixesComponent().getPrefixByCatalogId(catalogPrefixId) != null) {
            this.client.sendResponse(new UserNickIconsComposer(habbo));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT display_name, text, color, icon, effect, font, points, points_type FROM custom_prefixes_catalog WHERE id = ? AND enabled = 1 LIMIT 1")) {
            statement.setInt(1, catalogPrefixId);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return;
                }

                int points = set.getInt("points");
                int pointsType = set.getInt("points_type");

                if (points > 0 && habbo.getHabboInfo().getCurrencyAmount(pointsType) < points) {
                    this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Not enough points."));
                    return;
                }

                if (points > 0) {
                    habbo.getHabboInfo().addCurrencyAmount(pointsType, -points);
                    this.client.sendResponse(new UserCurrencyComposer(habbo));
                }

                var prefix = new UserPrefix(
                    habbo.getHabboInfo().getId(),
                    set.getString("text"),
                    set.getString("color"),
                    set.getString("icon"),
                    set.getString("effect"),
                    set.getString("font"),
                    catalogPrefixId,
                    set.getString("display_name"),
                    points,
                    pointsType,
                    false);
                prefix.run();
                habbo.getInventory().getPrefixesComponent().addPrefix(prefix);
                this.client.sendResponse(new UserNickIconsComposer(habbo));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while purchasing catalog prefix", e);
        }
    }
}
