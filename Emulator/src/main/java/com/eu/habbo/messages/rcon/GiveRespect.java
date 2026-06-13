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

public class GiveRespect extends RCONMessage<GiveRespect.JSONGiveRespect> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GiveRespect.class);


    public GiveRespect() {
        super(JSONGiveRespect.class);
    }

    @Override
    public void handle(Gson gson, JSONGiveRespect object) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);

        if (habbo != null) {
            habbo.getHabboStats().respectPointsReceived += object.respect_received;
            habbo.getHabboStats().respectPointsGiven += object.respect_given;
            habbo.getHabboStats().respectPointsToGive += object.daily_respects;
            habbo.getClient().sendResponse(new UserDataComposer(habbo));
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET respects_given = respects_given + ?, respects_received = respects_received + ?, daily_respect_points = daily_respect_points + ? WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, object.respect_given);
                statement.setInt(2, object.respect_received);
                statement.setInt(3, object.daily_respects);
                statement.setInt(4, object.user_id);
                statement.execute();
            } catch (SQLException e) {
                this.status = RCONMessage.SYSTEM_ERROR;
                LOGGER.error("Caught SQL exception", e);
            }

            this.message = "offline";
        }
    }

    static class JSONGiveRespect {
        public int user_id;
        public int respect_given = 0;
        public int respect_received = 0;
        public int daily_respects = 0;
    }
}
