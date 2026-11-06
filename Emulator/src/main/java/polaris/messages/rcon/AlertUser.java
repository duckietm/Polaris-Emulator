package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import polaris.Emulator;
import polaris.hotel.users.User;

public class AlertUser extends RCONMessage<AlertUser.JSONAlertUser> {

    public AlertUser() {
        super(JSONAlertUser.class);
    }

    @Override
    public void handle(Gson gson, JSONAlertUser object) {
        User user = Emulator.getGameEnvironment().getUserManager().getUser(object.user_id);

        if (user != null) {
            user.alert(object.message);
            return;
        }

        this.status = RCONMessage.USER_NOT_FOUND;
    }

    static class JSONAlertUser {

        @Positive(message = "invalid user")
        int user_id;

        @NotBlank(message = "invalid message")
        @Size(max = 4096, message = "invalid message")
        String message;
    }
}
