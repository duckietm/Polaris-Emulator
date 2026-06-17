package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;
import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public class HotelAlert extends RCONMessage<HotelAlert.JSONHotelAlert> {

    public HotelAlert() {
        super(JSONHotelAlert.class);
    }

    @Override
    public void handle(Gson gson, JSONHotelAlert object) {
        ServerMessage serverMessage;
        if (object.url.isEmpty()) {
            serverMessage = new GenericAlertComposer(object.message).compose();
        } else {
            serverMessage = new StaffAlertWithLinkComposer(object.message, object.url).compose();
        }

        if (serverMessage != null) {
            for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
                Habbo habbo = set.getValue();
                if (habbo.getHabboStats().blockStaffAlerts)
                    continue;

                habbo.getClient().sendResponse(serverMessage);
            }
        }
    }

    static class JSONHotelAlert {

        @NotBlank(message = "invalid message")
        @Size(max = 4096, message = "invalid message")
        public String message;


        @Size(max = 2048, message = "invalid url")
        @Pattern(regexp = "^$|https?://.+", message = "invalid url")
        public String url = "";
    }
}
