package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SetRank extends RCONMessage<SetRank.JSONSetRank> {

    public SetRank() {
        super(JSONSetRank.class);
    }

    @Override
    public void handle(Gson gson, JSONSetRank object) {
        int maxRank = SetRankRequestGuard.parseMaxRank(
                Emulator.getConfig().getValue("rcon.setrank.max_rank", String.valueOf(SetRankRequestGuard.DEFAULT_MAX_RANK)));
        String validationError = SetRankRequestGuard.validate(
                object.user_id,
                object.rank,
                maxRank,
                rankId -> Emulator.getGameEnvironment().getPermissionsManager().rankExists(rankId));
        if (validationError != null) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = validationError;
            return;
        }

        if (!userExists(object.user_id)) {
            this.status = RCONMessage.HABBO_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        try {
            Emulator.getGameEnvironment().getHabboManager().setRank(object.user_id, object.rank);
        } catch (Exception e) {
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "invalid rank";
            return;
        }

        this.message = "updated offline user";

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);

        if (habbo != null) {
            this.message = "updated online user";
        }
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
        } catch (Exception e) {
            return false;
        }
    }

    static class JSONSetRank {

        @Positive(message = "invalid user")
        public int user_id;


        @Positive(message = "invalid rank")
        public int rank;
    }
}
