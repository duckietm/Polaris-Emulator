package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.modtool.ModToolBanType;
import polaris.messages.incoming.housekeeping.HousekeepingOperator;

/**
 * RCON {@code banuser}: bans a user for {@code duration} seconds through
 * {@code ModToolManager.banAsSystem}, the moderator-less path of the mod tool's ban: an online user
 * sees the ban and is disconnected, an IP or machine ban also bans the accounts sharing that
 * address or machine, and a machine/IP ban without a known machine id/IP falls back to IP/account
 * the way the mod tool's does. Audited as {@code user.ban} by the All-in-1 Manager.
 */
public class BanUser extends RCONMessage<BanUser.JSON> {
    /** The bans.ban_reason column is varchar(200): a longer reason fails the insert after the answer went out. */
    static final int MAX_REASON_LENGTH = 200;

    static final List<String> TYPES = List.of("account", "ip", "machine", "super");

    private final Supplier<GameEnvironment> environment;

    public BanUser() {
        this(WiredPlatform::gameEnvironment);
    }

    BanUser(Supplier<GameEnvironment> environment) {
        super(JSON.class);
        this.environment = environment;
    }

    @Override
    public void handle(Gson gson, JSON json) {
        ModToolBanType type = banType(json.type);

        if (type == null) {
            this.status = STATUS_ERROR;
            this.message = "type must be one of: " + String.join(", ", TYPES);
            return;
        }

        String reason = json.reason == null ? "" : json.reason.trim();
        GameEnvironment environment = this.environment.get();

        if (environment.getUserManager().getUserInfo(json.user_id) == null) {
            this.status = USER_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        List<ModToolBan> bans = environment.getModToolManager().banAsSystem(json.user_id, reason, json.duration, type);

        if (bans == null || bans.isEmpty()) {
            this.status = SYSTEM_ERROR;
            this.message = "ban failed";
            return;
        }

        HousekeepingAuditLog.log(
                HousekeepingOperator.SYSTEM_ID,
                HousekeepingOperator.SYSTEM_NAME,
                "user.ban",
                json.user_id,
                "seconds=" + json.duration + " type=" + bans.get(0).type.getType() + " accounts=" + bans.size()
                        + " reason=" + auditValue(reason),
                HousekeepingOperator.SYSTEM_IP);
    }

    /** "account" (also when left out), "ip", "machine" or "super"; null for anything else. */
    static ModToolBanType banType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);

        return switch (type) {
            case "", "account" -> ModToolBanType.ACCOUNT;
            case "ip" -> ModToolBanType.IP;
            case "machine" -> ModToolBanType.MACHINE;
            case "super" -> ModToolBanType.SUPER;
            default -> null;
        };
    }

    /** Free text on one line, for the audit detail. */
    static String auditValue(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }

    static class JSON {
        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "duration must be a positive number of seconds")
        public int duration;

        @Size(max = MAX_REASON_LENGTH, message = "reason must be at most 200 characters")
        public String reason;

        public String type;
    }
}
