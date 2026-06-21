package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ChangeUsername extends RCONMessage<ChangeUsername.JSON> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeUsername.class);

    public ChangeUsername() {
        super(ChangeUsername.JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        try {
            if (json.user_id <= 0) {
                this.status = RCONMessage.HABBO_NOT_FOUND;
                this.message = "User not found";
                return;
            }

            boolean success = true;

            Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(json.user_id);
            if (habbo != null) {
                if (json.canChange)
                    habbo.alert(Emulator.getTexts().getValue("rcon.alert.user.change_username"));

                habbo.getHabboStats().allowNameChange = json.canChange;
                habbo.getClient().sendResponse(new UserDataComposer(habbo));
            } else {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET allow_name_change = ? WHERE user_id = ? LIMIT 1")) {
                        statement.setBoolean(1, json.canChange);
                        statement.setInt(2, json.user_id);

                        success = statement.executeUpdate() >= 1;
                    } catch (SQLException sqlException) {
                        LOGGER.error("Caught SQL exception", sqlException);
                    }
                } catch (SQLException sqlException) {
                    LOGGER.error("Caught SQL exception", sqlException);
                }
            }

            this.status = success ? RCONMessage.STATUS_OK : RCONMessage.STATUS_ERROR;
            this.message = success ? "Sent successfully." : "There was an error updating this user.";
        }
        catch (Exception e) {
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "Exception occurred";
            LOGGER.error("Exception occurred", e);
        }
    }

    static class JSON {
        public int user_id;

        public boolean canChange;
    }
}
