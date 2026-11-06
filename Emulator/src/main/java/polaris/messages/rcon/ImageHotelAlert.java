package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;

public class ImageHotelAlert extends RCONMessage<ImageHotelAlert.JSON> {
    public ImageHotelAlert() {
        super(ImageHotelAlert.JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        Map<String, String> keys = new HashMap<>();

        if (!json.message.isEmpty()) {
            keys.put("message", json.message);
        }

        if (!json.url.isEmpty()) {
            keys.put("linkUrl", json.url);
        }

        if (!json.url_message.isEmpty()) {
            keys.put("linkTitle", json.url_message);
        }

        if (!json.title.isEmpty()) {
            keys.put("title", json.title);
        }

        if (!json.display_type.isEmpty()) {
            keys.put("display", json.display_type);
        }

        if (!json.image.isEmpty()) {
            keys.put("image", json.image);
        }

        ServerMessage message = new BubbleAlertComposer(json.bubble_key, keys).compose();

        for (Map.Entry<Integer, User> set :
                Emulator.getGameEnvironment().getUserManager().getOnlineUsers().entrySet()) {
            User user = set.getValue();
            if (user.getUserStats().blockStaffAlerts) continue;

            user.getClient().sendResponse(message);
        }
    }

    static class JSON {

        @NotBlank(message = "invalid bubble")
        @Size(max = 64, message = "invalid bubble")
        @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "invalid bubble")
        public String bubble_key = "";

        @Size(max = 4096, message = "invalid message")
        public String message = "";

        @Size(max = 2048, message = "invalid url")
        @Pattern(regexp = "^$|https?://.+", message = "invalid url")
        public String url = "";

        @Size(max = 256, message = "invalid url title")
        public String url_message = "";

        @Size(max = 256, message = "invalid title")
        public String title = "";

        @Size(max = 32, message = "invalid display")
        @Pattern(regexp = "^$|[A-Za-z0-9_.-]+", message = "invalid display")
        public String display_type = "";

        @Size(max = 2048, message = "invalid image")
        public String image = "";
    }
}
