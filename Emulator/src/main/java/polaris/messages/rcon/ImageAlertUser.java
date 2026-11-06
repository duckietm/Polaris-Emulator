package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;

public class ImageAlertUser extends RCONMessage<ImageAlertUser.JSON> {
    public ImageAlertUser() {
        super(ImageAlertUser.JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        User user = Emulator.getGameEnvironment().getUserManager().getUser(json.user_id);

        if (user == null) {
            this.status = USER_NOT_FOUND;
            return;
        }

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

        user.getClient().sendResponse(new BubbleAlertComposer(json.bubble_key, keys));
    }

    static class JSON {

        @Positive(message = "invalid user")
        public int user_id;

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
