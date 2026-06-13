package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class GiveCredits extends RCONMessage<GiveCredits.JSONGiveCredits> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GiveCredits.class);


    public GiveCredits() {
        super(JSONGiveCredits.class);
    }

    @Override
    public void handle(Gson gson, JSONGiveCredits object) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);

        if (habbo != null) {
            habbo.giveCredits(object.credits);
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE users SET credits = credits + ? WHERE id = ? LIMIT 1")) {
                statement.setInt(1, object.credits);
                statement.setInt(2, object.user_id);
                if (statement.executeUpdate() == 0) {
                    this.status = RCONMessage.HABBO_NOT_FOUND;
                    return;
                }
            } catch (SQLException e) {
                this.status = RCONMessage.SYSTEM_ERROR;
                LOGGER.error("Caught SQL exception", e);
                return;
            }

            this.message = "offline";
        }
    }

    static class JSONGiveCredits {

        public int user_id;


        public int credits;
    }
}
