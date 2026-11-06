package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import polaris.Emulator;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.users.User;

public class SetRank extends RCONMessage<SetRank.JSONSetRank> {

    public SetRank() {
        super(JSONSetRank.class);
    }

    @Override
    public void handle(Gson gson, JSONSetRank object) {
        int maxRank = SetRankRequestGuard.parseMaxRank(Emulator.getConfig()
                .getValue("rcon.setrank.max_rank", String.valueOf(SetRankRequestGuard.DEFAULT_MAX_RANK)));
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
            this.status = RCONMessage.USER_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(object.user_id);
        String fromRank = user != null && user.getUserInfo().getRank() != null
                ? String.valueOf(user.getUserInfo().getRank().getId())
                : "?";

        try {
            Emulator.getGameEnvironment().getUserManager().setRank(object.user_id, object.rank, 0, "rcon", "rcon");
        } catch (Exception e) {
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "invalid rank";
            return;
        }

        HousekeepingAuditLog.log(
                0,
                "rcon",
                "user.set_rank",
                object.user_id,
                "fromRankId=" + fromRank + " rankId=" + object.rank + " via=rcon",
                "");

        this.message = user != null ? "updated online user" : "updated offline user";
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
