package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.generic.alerts.GenericAlertComposer;
import polaris.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;

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
            for (Map.Entry<Integer, User> set : Emulator.getGameEnvironment()
                    .getUserManager()
                    .getOnlineUsers()
                    .entrySet()) {
                User user = set.getValue();
                if (user.getUserStats().blockStaffAlerts) continue;

                user.getClient().sendResponse(serverMessage);
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
