package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.users.UserClothesComposer;

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
            this.status = RCONMessage.USER_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        if (!clothingExists(object.clothing_id)) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = "clothing not found";
            return;
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(object.user_id);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO users_clothing (user_id, clothing_id) VALUES (?, ?)")) {
            statement.setInt(1, object.user_id);
            statement.setInt(2, object.clothing_id);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "failed to grant clothing";
            return;
        }

        if (user != null) {
            GameClient client = user.getClient();

            if (client != null) {
                user.getInventory().getWardrobeComponent().getClothing().add(object.clothing_id);
                client.sendResponse(new UserClothesComposer(user));
                client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FIGURESET_REDEEMED.key));
            }
        }

        this.message = "granted clothing";
    }

    private static boolean userExists(int userId) {
        User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);
        if (user != null) {
            return true;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM users WHERE id = ? LIMIT 1")) {
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
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM catalog_clothing WHERE id = ? LIMIT 1")) {
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
        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid clothing")
        public int clothing_id;
    }
}
