package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.google.gson.Gson;
import gnu.trove.map.hash.THashMap;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ImageAlertUser extends RCONMessage<ImageAlertUser.JSON> {
    public ImageAlertUser() {
        super(ImageAlertUser.JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(json.user_id);

        if (habbo == null) {
            this.status = HABBO_NOT_FOUND;
            return;
        }

        THashMap<String, String> keys = new THashMap<>();

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

        habbo.getClient().sendResponse(new BubbleAlertComposer(json.bubble_key, keys));
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
