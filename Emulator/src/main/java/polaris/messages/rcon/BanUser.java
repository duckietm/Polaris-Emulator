package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import polaris.hotel.modtool.ConsoleModeration;
import polaris.hotel.modtool.ModToolBanType;

/**
 * {@code banuser}: bans a player from the hotel's console. Data: {@code user_id}, {@code duration} in seconds (at most
 * {@link ConsoleModeration#MAX_BAN_SECONDS}), {@code reason} and an optional {@code type}: {@code account} (default),
 * {@code ip}, {@code machine} or {@code super} (address and machine). An online player sees the ban and is disconnected.
 */
public class BanUser extends RCONMessage<BanUser.BanUserJSON> {

    public BanUser() {
        super(BanUserJSON.class);
    }

    @Override
    public void handle(Gson gson, BanUserJSON json) {
        ConsoleModeration moderation = ConsoleModeration.installed();
        if (moderation == null) {
            this.status = STATUS_ERROR;
            this.message = "The hotel is still starting.";
            return;
        }

        ModToolBanType type = type(json.type);
        if (type == null) {
            this.status = STATUS_ERROR;
            this.message = "type must be account, ip, machine or super";
            return;
        }

        switch (moderation.ban(json.user_id, json.duration, json.reason.trim(), type)) {
            case BANNED -> this.message = "banned";
            case USER_NOT_FOUND -> this.status = USER_NOT_FOUND;
            case FAILED -> {
                this.status = SYSTEM_ERROR;
                this.message = "the ban could not be stored";
            }
        }
    }

    /** The ban type named by {@code text}; account when empty, null when it names none. */
    static ModToolBanType type(String text) {
        if (text == null || text.isBlank()) return ModToolBanType.ACCOUNT;
        ModToolBanType type = ModToolBanType.fromString(text.trim());
        return type == ModToolBanType.UNKNOWN ? null : type;
    }

    static class BanUserJSON {
        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid duration")
        @Max(value = ConsoleModeration.MAX_BAN_SECONDS, message = "duration is too long")
        public int duration;

        @NotBlank(message = "a reason is required")
        @Size(max = 200, message = "the reason is longer than 200 characters")
        public String reason;

        public String type;
    }
}
