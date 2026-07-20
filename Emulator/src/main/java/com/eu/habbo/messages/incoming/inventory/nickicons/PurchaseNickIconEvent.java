package com.eu.habbo.messages.incoming.inventory.nickicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.habbohotel.users.UserNickIcon;
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

public class PurchaseNickIconEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseNickIconEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) {
            return;
        }

        String requestedIconKey = normalizeIconKey(this.packet.readString());

        if (requestedIconKey.isEmpty()) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Invalid nick icon selected."));
            return;
        }

        if (habbo.getInventory().getNickIconsComponent().getNickIconByKey(requestedIconKey) != null) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "You already own this nick icon."));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT points, points_type, enabled FROM custom_nick_icons_catalog WHERE icon_key = ? LIMIT 1")) {
            statement.setString(1, requestedIconKey);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next() || !set.getBoolean("enabled")) {
                    this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "This nick icon is not available."));
                    return;
                }

                int points = set.getInt("points");
                int pointsType = set.getInt("points_type");

                if (points > 0 && habbo.getHabboInfo().getCurrencyAmount(pointsType) < points) {
                    this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Not enough points."));
                    return;
                }

                if (points > 0) {
                    try {
                        LedgerWalletMutation.execute(habbo, new EconomyOperation(
                                EconomyOperationId.create(
                                        "nick-icon:" + habbo.getHabboInfo().getId()
                                                + ":" + requestedIconKey),
                                habbo.getHabboInfo().getId(),
                                habbo.getHabboInfo().getId(),
                                "nick_icon_purchase",
                                "inventory.nick_icon.purchase",
                                pointsType,
                                -points,
                                null,
                                requestedIconKey));
                    } catch (IllegalArgumentException exception) {
                        this.client.sendResponse(new BubbleAlertComposer(
                                BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key,
                                "Not enough points."));
                        return;
                    }
                    this.client.sendResponse(new UserCurrencyComposer(habbo));
                }

                UserNickIcon nickIcon = new UserNickIcon(habbo.getHabboInfo().getId(), requestedIconKey);
                nickIcon.run();
                habbo.getInventory().getNickIconsComponent().addNickIcon(nickIcon);

                this.client.sendResponse(new UserNickIconsComposer(habbo));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Unable to purchase this nick icon right now."));
        }
    }

    private String normalizeIconKey(String iconKey) {
        if (iconKey == null) {
            return "";
        }

        String normalized = iconKey.trim().toLowerCase();

        if (normalized.endsWith(".gif")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }

        return normalized.matches("^[a-z0-9_-]+$") ? normalized : "";
    }
}
