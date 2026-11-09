package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import java.util.function.Supplier;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.housekeeping.HousekeepingOperator;

/**
 * RCON {@code unbanuser}: lifts every active ban of a user (account, IP, machine and super bans
 * recorded on the user) through {@code ModToolManager.unban} without a moderator, which lifts them
 * whoever placed them. Audited as {@code user.unban} by the All-in-1 Manager.
 */
public class UnbanUser extends RCONMessage<UnbanUser.JSON> {
    static final String NO_ACTIVE_BAN = "no active ban";

    private final Supplier<GameEnvironment> environment;

    public UnbanUser() {
        this(WiredPlatform::gameEnvironment);
    }

    UnbanUser(Supplier<GameEnvironment> environment) {
        super(JSON.class);
        this.environment = environment;
    }

    @Override
    public void handle(Gson gson, JSON json) {
        GameEnvironment environment = this.environment.get();
        UserInfo info = environment.getUserManager().getUserInfo(json.user_id);

        if (info == null) {
            this.status = USER_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        if (!environment.getModToolManager().unban(info.getUsername())) {
            this.status = STATUS_ERROR;
            this.message = NO_ACTIVE_BAN;
            return;
        }

        HousekeepingAuditLog.log(
                HousekeepingOperator.SYSTEM_ID,
                HousekeepingOperator.SYSTEM_NAME,
                "user.unban",
                json.user_id,
                "username=" + info.getUsername(),
                HousekeepingOperator.SYSTEM_IP);
    }

    static class JSON {
        @Positive(message = "invalid user")
        public int user_id;
    }
}
