package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.users.UserClothesComposer;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GiveUserClothing extends RCONMessage<GiveUserClothing.JSONGiveUserClothing> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GiveUserClothing.class);

    public GiveUserClothing() {
        super(GiveUserClothing.JSONGiveUserClothing.class);
    }

    @Override
    public void handle(Gson gson, GiveUserClothing.JSONGiveUserClothing object) {
        if (object.user_id <= 0 || object.clothing_id <= 0) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = "invalid user or clothing";
            return;
        }

        if (!userExists(object.user_id)) {
            this.status = RCONMessage.HABBO_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        if (!clothingExists(object.clothing_id)) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = "clothing not found";
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO users_clothing (user_id, clothing_id) VALUES (?, ?)")) {
            statement.setInt(1, object.user_id);
            statement.setInt(2, object.clothing_id);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "failed to grant clothing";
            return;
        }

        if (habbo != null) {
            GameClient client = habbo.getClient();

            if (client != null) {
                habbo.getInventory().getWardrobeComponent().getClothing().add(object.clothing_id);
                client.sendResponse(new UserClothesComposer(habbo));
                client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FIGURESET_REDEEMED.key));
            }
        }

        this.message = "granted clothing";
    }

    private static boolean userExists(int userId) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (habbo != null) {
            return true;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }

    private static boolean clothingExists(int clothingId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id FROM catalog_clothing WHERE id = ? LIMIT 1")) {
            statement.setInt(1, clothingId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }

    static class JSONGiveUserClothing {
        public int user_id;
        public int clothing_id;
    }
}
