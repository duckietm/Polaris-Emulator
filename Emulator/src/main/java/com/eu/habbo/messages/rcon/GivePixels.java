package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class GivePixels extends RCONMessage<GivePixels.JSONGivePixels> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GivePixels.class);


    public GivePixels() {
        super(JSONGivePixels.class);
    }

    @Override
    public void handle(Gson gson, JSONGivePixels object) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);

        if (habbo != null) {
            habbo.givePixels(object.pixels);
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO users_currency (`user_id`, `type`, `amount`) VALUES (?, 0, ?) ON DUPLICATE KEY UPDATE amount = amount + ?")) {
                statement.setInt(1, object.user_id);
                statement.setInt(2, object.pixels);
                statement.setInt(3, object.pixels);
                statement.executeUpdate();
            } catch (SQLException e) {
                this.status = RCONMessage.SYSTEM_ERROR;
                LOGGER.error("Caught SQL exception", e);
            }

            this.message = "offline";
        }
    }

    static class JSONGivePixels {

        public int user_id;


        public int pixels;
    }
}
