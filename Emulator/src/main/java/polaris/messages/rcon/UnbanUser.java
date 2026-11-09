package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import polaris.hotel.modtool.ConsoleModeration;

/**
 * {@code unbanuser}: lifts every active ban of a player from the hotel's console. Data: {@code user_id}. Answers
 * {@link #USER_NOT_FOUND} when the player has no active ban.
 */
public class UnbanUser extends RCONMessage<UnbanUser.UnbanUserJSON> {

    public UnbanUser() {
        super(UnbanUserJSON.class);
    }

    @Override
    public void handle(Gson gson, UnbanUserJSON json) {
        ConsoleModeration moderation = ConsoleModeration.installed();
        if (moderation == null) {
            this.status = STATUS_ERROR;
            this.message = "The hotel is still starting.";
            return;
        }

        if (moderation.unban(json.user_id)) {
            this.message = "unbanned";
        } else {
            this.status = USER_NOT_FOUND;
            this.message = "no active ban";
        }
    }

    static class UnbanUserJSON {
        @Positive(message = "invalid user")
        public int user_id;
    }
}
