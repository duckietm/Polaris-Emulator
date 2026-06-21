package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class AlertUser extends RCONMessage<AlertUser.JSONAlertUser> {

    public AlertUser() {
        super(JSONAlertUser.class);
    }

    @Override
    public void handle(Gson gson, JSONAlertUser object) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);

        if (habbo != null) {
            habbo.alert(object.message);
            return;
        }

        this.status = RCONMessage.HABBO_NOT_FOUND;
    }

    static class JSONAlertUser {

        @Positive(message = "invalid user")
        int user_id;


        @NotBlank(message = "invalid message")
        @Size(max = 4096, message = "invalid message")
        String message;
    }
}
