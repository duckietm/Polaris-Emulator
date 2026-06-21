package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class TalkUser extends RCONMessage<TalkUser.JSON> {
    public TalkUser() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(json.user_id);

        if (habbo != null) {
            json.type = json.type.toLowerCase();
            switch (json.type) {
                case "talk":
                    habbo.talk(json.message, json.bubble_id != -1 ? RoomChatMessageBubbles.getBubble(json.bubble_id) : habbo.getHabboStats().chatColor);
                    break;
                case "whisper":
                    habbo.whisper(json.message, json.bubble_id != -1 ? RoomChatMessageBubbles.getBubble(json.bubble_id) : habbo.getHabboStats().chatColor);
                    break;
                case "shout":
                    habbo.shout(json.message, json.bubble_id != -1 ? RoomChatMessageBubbles.getBubble(json.bubble_id) : habbo.getHabboStats().chatColor);
                    break;
                default:
                    this.status = STATUS_ERROR;
                    this.message = "Talk type: " + json.type + " not found! Use talk, whisper or shout!";
            }
        } else {
            this.status = HABBO_NOT_FOUND;
            this.message = "offline";
        }
    }

    static class JSON {

        @NotBlank(message = "invalid type")
        @Size(max = 16, message = "invalid type")
        public String type;


        @Positive(message = "invalid user")
        public int user_id;


        public int bubble_id = -1;


        @NotBlank(message = "invalid message")
        @Size(max = 512, message = "invalid message")
        public String message;
    }
}
