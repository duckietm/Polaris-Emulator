package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class IgnoreUser extends RCONMessage<IgnoreUser.JSONIgnoreUser> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IgnoreUser.class);

    public IgnoreUser() {
        super(JSONIgnoreUser.class);
    }

    @Override
    public void handle(Gson gson, JSONIgnoreUser object) {
        if (object.user_id == object.target_id) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = "cannot ignore self";
            return;
        }

        if (!RconUserLookup.userExists(object.user_id) || !RconUserLookup.userExists(object.target_id)) {
            this.status = RCONMessage.HABBO_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);

        if (habbo != null) {
            habbo.getHabboStats().ignoreUser(habbo.getClient(), object.target_id);
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO users_ignored (user_id, target_id) VALUES (?, ?)")) {
                statement.setInt(1, object.user_id);
                statement.setInt(2, object.target_id);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            this.message = "offline";
        }
    }

    static class JSONIgnoreUser {

        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid target")
        public int target_id;
    }
}
