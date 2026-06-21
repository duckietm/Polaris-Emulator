package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MuteUser extends RCONMessage<MuteUser.JSON> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MuteUser.class);
    static final int DEFAULT_MAX_DURATION_SECONDS = 604_800;

    public MuteUser() {
        super(MuteUser.JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        int maxDuration = parseMaxDuration(Emulator.getConfig().getValue("rcon.mute.max_duration_seconds", String.valueOf(DEFAULT_MAX_DURATION_SECONDS)));
        if (json.duration < 0 || json.duration > maxDuration) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = "duration must be between 0 and " + maxDuration + " seconds";
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(json.user_id);

        if (habbo != null) {
            if (json.duration == 0) {
                habbo.unMute();
            } else {
                habbo.mute(json.duration, false);
            }
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET mute_end_timestamp = ? WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, json.duration == 0 ? 0 : Emulator.getIntUnixTimestamp() + json.duration);
                statement.setInt(2, json.user_id);
                if (statement.executeUpdate() == 0) {
                    this.status = HABBO_NOT_FOUND;
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    static int parseMaxDuration(String configured) {
        try {
            int parsed = Integer.parseInt(configured);
            if (parsed >= 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        return DEFAULT_MAX_DURATION_SECONDS;
    }

    static class JSON {

        @Positive(message = "invalid user")
        public int user_id;

        @Min(value = 0, message = "invalid duration")
        public int duration;
    }
}
